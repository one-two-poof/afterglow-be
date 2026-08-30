package com.afterglow.service;

import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.PlaceType;
import com.afterglow.kakao.KakaoPlace;
import com.afterglow.kakao.KakaoPlaceClient;
import com.afterglow.kakao.SeoulDistricts;
import com.afterglow.repository.HospitalAccommodationRepository;
import com.afterglow.web.dto.TourApiListItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관광공사 TourAPI 4.0(KorService2, contentTypeId=32 숙박) 서울 전체 목록을 카카오 로컬 API(AD5
 * 숙박 카테고리)와 매칭해 {@link HospitalAccommodation} 테이블의 ACCOMMODATION 행을 채운다.
 * {@link HospitalSyncService}의 관광공사+카카오 매칭 단계와 같은 패턴 — 매칭되면 기존 카카오 기반
 * 행을 그대로 찾아 갱신하므로 같은 숙소가 중복 생성되지 않는다.
 *
 * <p>2단계로 나뉜다.
 * <ol>
 *   <li>1단계: TourAPI 목록 항목의 이름으로 카카오를 검색해 매칭 시도(기존 로직).</li>
 *   <li>2단계: TourAPI 목록과 무관하게 카카오 AD5(숙박) 그룹코드로 서울 25개 구를 통째로 스윕해서
 *       TourAPI에 없는 숙소(게스트하우스·소규모 모텔 등)도 찾는다. 이미 존재하는 행이면(같은
 *       kakaoPlaceId) 이름·주소·좌표·source를 최신으로 갱신하고, 없으면 source=KAKAO로 새로 만든다
 *       — {@link HospitalSyncService}/{@link AttractionSyncService}의 카카오 재발견 갱신과 같은 패턴.</li>
 * </ol>
 */
@Service
public class AccommodationSyncService {

    private static final Logger log = LoggerFactory.getLogger(AccommodationSyncService.class);

    private static final int ACCOMMODATION_CONTENT_TYPE_ID = 32;
    private static final String SOURCE_BOTH = "TOURISM_API+KAKAO";
    private static final String SOURCE_TOURISM_ONLY = "TOURISM_API";
    private static final String SOURCE_KAKAO_ONLY = "KAKAO";
    private static final String ACCOMMODATION_CATEGORY_GROUP_CODE = "AD5";
    private static final String ACCOMMODATION_CATEGORY_PREFIX = "여행 > 숙박";

    private final TourApiService tourApiService;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final HospitalAccommodationRepository hospitalAccommodationRepository;
    private final PlaceTranslationService placeTranslationService;

    public AccommodationSyncService(
            TourApiService tourApiService,
            KakaoPlaceClient kakaoPlaceClient,
            HospitalAccommodationRepository hospitalAccommodationRepository,
            PlaceTranslationService placeTranslationService) {
        this.tourApiService = tourApiService;
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.hospitalAccommodationRepository = hospitalAccommodationRepository;
        this.placeTranslationService = placeTranslationService;
    }

    @Transactional
    public SyncResult sync() {
        Instant syncedAt = Instant.now();
        List<TourApiListItem> items = tourApiService.listSeoulByContentType(ACCOMMODATION_CONTENT_TYPE_ID);
        // 같은 목록을 언어별로 한 번 더 조회해 contentId→title 맵을 미리 만들어 둔다(건별 API 호출 방지).
        Map<String, String> jaTitles = tourApiService.titleByContentId(ACCOMMODATION_CONTENT_TYPE_ID, "ja");
        Map<String, String> enTitles = tourApiService.titleByContentId(ACCOMMODATION_CONTENT_TYPE_ID, "en");

        int matchedBoth = 0;
        int tourismOnly = 0;
        int skippedNoCoords = 0;

        for (TourApiListItem item : items) {
            String query = extractSearchQuery(item.title());
            Optional<KakaoPlace> match;
            try {
                match = kakaoPlaceClient.findAccommodation(query, item.mapX(), item.mapY());
            } catch (Exception e) {
                log.warn("카카오 검색 실패: title={}, error={}", item.title(), e.getMessage());
                match = Optional.empty();
            }

            if (match.isPresent()) {
                upsertMatched(item, match.get(), syncedAt, jaTitles, enTitles);
                matchedBoth++;
            } else {
                boolean saved = upsertTourismOnly(item, syncedAt, jaTitles, enTitles);
                if (saved) {
                    tourismOnly++;
                } else {
                    skippedNoCoords++;
                }
            }
        }

        KakaoSweepResult kakaoSweep = collectKakaoAccommodations(syncedAt);

        log.info(
                "숙소 동기화 완료: 관광공사={}건 (양쪽매칭={}, 관광공사단독={}, 좌표없음스킵={}) / 카카오 AD5 스윕: {}",
                items.size(), matchedBoth, tourismOnly, skippedNoCoords, kakaoSweep);

        return new SyncResult(items.size(), matchedBoth, tourismOnly, skippedNoCoords, kakaoSweep, syncedAt);
    }

    /**
     * TourAPI 목록과 무관하게 카카오 AD5(숙박) 그룹코드로 서울 25개 구를 스윕한다. 구 이름만 쿼리로
     * 넣고 그룹코드로 필터링(예: "강남구" + AD5) — {@link AttractionSyncService}가 CE7/CT1/AT4를
     * 스윕하는 것과 같은 방식이다. AD5엔 "여행 > 숙박" 경로가 아닌 게 섞여 나올 수 있어 categoryName
     * 접두어로 한 번 더 거른다.
     */
    private KakaoSweepResult collectKakaoAccommodations(Instant syncedAt) {
        Map<String, KakaoPlace> discovered = new LinkedHashMap<>();
        for (SeoulDistricts.Center district : SeoulDistricts.ALL) {
            List<KakaoPlace> found;
            try {
                found = kakaoPlaceClient.searchKeywordAll(district.name(), ACCOMMODATION_CATEGORY_GROUP_CODE);
            } catch (Exception e) {
                log.warn("카카오 숙박 스윕 실패: district={}, error={}", district.name(), e.getMessage());
                continue;
            }
            for (KakaoPlace place : found) {
                if (place.categoryName() != null && place.categoryName().startsWith(ACCOMMODATION_CATEGORY_PREFIX)) {
                    discovered.putIfAbsent(place.id(), place);
                }
            }
        }

        int newlyCreated = 0;
        int merged = 0;
        for (KakaoPlace place : discovered.values()) {
            HospitalAccommodation existing = hospitalAccommodationRepository.findByPlaceId(place.id()).orElse(null);
            if (existing == null) {
                hospitalAccommodationRepository.save(new HospitalAccommodation(
                        place.id(),
                        null,
                        place.placeName(),
                        place.categoryGroupName(),
                        place.addressName(),
                        place.mapX(),
                        place.mapY(),
                        null,
                        place.phone(),
                        place.placeUrl(),
                        PlaceType.ACCOMMODATION,
                        SOURCE_KAKAO_ONLY,
                        syncedAt));
                newlyCreated++;
            } else {
                merged++;
                // 오늘 카카오 검색으로 다시 확인된 데이터이므로 이름/주소/좌표/source를 최신으로 갱신한다.
                // 관리자가 직접 등록/수정한 행(MANUAL)만 예외로 보호한다.
                if (!"MANUAL".equals(existing.getSource())) {
                    existing.updateFromSync(
                            place.placeName(),
                            place.addressName(),
                            place.mapX(),
                            place.mapY(),
                            null,
                            place.phone(),
                            place.placeUrl(),
                            SOURCE_KAKAO_ONLY,
                            syncedAt);
                }
            }
        }
        return new KakaoSweepResult(discovered.size(), newlyCreated, merged);
    }

    /** contentId가 이번 목록에 있으면 TourAPI 공식 번역을 적용한다(자리만 만들고, 없으면 백필 스케줄러가 나중에 채움). */
    private void applyTourApiTranslations(
            Long placeId, String contentId, Map<String, String> jaTitles, Map<String, String> enTitles) {
        if (contentId == null || contentId.isBlank()) {
            return;
        }
        placeTranslationService.applyPlaceName(PlaceType.ACCOMMODATION, placeId, "ja", jaTitles.get(contentId), "TOURAPI_JPN");
        placeTranslationService.applyPlaceName(PlaceType.ACCOMMODATION, placeId, "en", enTitles.get(contentId), "TOURAPI_ENG");
    }

    private void upsertMatched(
            TourApiListItem item,
            KakaoPlace kakaoPlace,
            Instant syncedAt,
            Map<String, String> jaTitles,
            Map<String, String> enTitles) {
        HospitalAccommodation place = hospitalAccommodationRepository.findByPlaceId(kakaoPlace.id()).orElse(null);
        String image = firstNonBlank(item.firstImage(), null);
        if (place == null) {
            place = hospitalAccommodationRepository.save(new HospitalAccommodation(
                    kakaoPlace.id(),
                    item.contentId(),
                    kakaoPlace.placeName(),
                    kakaoPlace.categoryGroupName(),
                    kakaoPlace.addressName(),
                    kakaoPlace.mapX(),
                    kakaoPlace.mapY(),
                    image,
                    kakaoPlace.phone(),
                    kakaoPlace.placeUrl(),
                    PlaceType.ACCOMMODATION,
                    SOURCE_BOTH,
                    syncedAt));
        } else {
            place.updateFromSync(
                    kakaoPlace.placeName(),
                    kakaoPlace.addressName(),
                    kakaoPlace.mapX(),
                    kakaoPlace.mapY(),
                    image,
                    kakaoPlace.phone(),
                    kakaoPlace.placeUrl(),
                    SOURCE_BOTH,
                    syncedAt);
        }
        applyTourApiTranslations(place.getId(), item.contentId(), jaTitles, enTitles);
    }

    /** 카카오 매칭 실패 — 관광공사 원본만으로 저장. 좌표가 없으면 스킵하고 false를 반환한다. */
    private boolean upsertTourismOnly(
            TourApiListItem item, Instant syncedAt, Map<String, String> jaTitles, Map<String, String> enTitles) {
        BigDecimal mapX = parseOrNull(item.mapX());
        BigDecimal mapY = parseOrNull(item.mapY());
        if (mapX == null || mapY == null) {
            log.info("관광공사 좌표 없음, 스킵: title={}", item.title());
            return false;
        }

        String address = firstNonBlank(item.addr1(), item.addr2());
        HospitalAccommodation place = hospitalAccommodationRepository.findByTourismContentId(item.contentId()).orElse(null);
        if (place == null) {
            place = hospitalAccommodationRepository.save(new HospitalAccommodation(
                    null,
                    item.contentId(),
                    item.title(),
                    "숙박",
                    address,
                    mapX,
                    mapY,
                    firstNonBlank(item.firstImage(), null),
                    item.tel(),
                    null,
                    PlaceType.ACCOMMODATION,
                    SOURCE_TOURISM_ONLY,
                    syncedAt));
        } else {
            place.updateFromSync(
                    item.title(),
                    address,
                    mapX,
                    mapY,
                    firstNonBlank(item.firstImage(), null),
                    firstNonBlank(item.tel(), place.getPhone()),
                    place.getPlaceUrl(),
                    SOURCE_TOURISM_ONLY,
                    syncedAt);
        }
        applyTourApiTranslations(place.getId(), item.contentId(), jaTitles, enTitles);
        return true;
    }

    private static BigDecimal parseOrNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a;
        if (StringUtils.hasText(b)) return b;
        return null;
    }

    /**
     * TourAPI 숙박 title은 "강남스테이힐(Gangnam Stay Hill)"처럼 한글 상호명 뒤에
     * 괄호로 영문명이 붙는 경우가 많음. 카카오는 한글 상호명 기준 검색이 잘 맞으므로
     * 괄호 앞부분을 검색어로 쓰고, 괄호가 없으면 원문 그대로 쓴다.
     */
    private static String extractSearchQuery(String title) {
        if (title == null) return null;
        int open = title.indexOf('(');
        if (open > 0) {
            String prefix = title.substring(0, open).trim();
            if (!prefix.isEmpty()) return prefix;
        }
        return title;
    }

    public record KakaoSweepResult(int discovered, int newlyCreated, int merged) {
    }

    public record SyncResult(
            int tourismFetched,
            int matchedBoth,
            int tourismOnly,
            int skippedNoCoords,
            KakaoSweepResult kakaoSweep,
            Instant syncedAt) {
    }
}
