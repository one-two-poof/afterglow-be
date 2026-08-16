package com.afterglow.service;

import com.afterglow.domain.Hospital;
import com.afterglow.kakao.KakaoPlace;
import com.afterglow.kakao.KakaoPlaceClient;
import com.afterglow.kakao.SeoulDistricts;
import com.afterglow.repository.HospitalRepository;
import com.afterglow.web.dto.MedicalTourismListItem;
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
 * 관광공사 의료관광 목록과 카카오 로컬 API(HP8 병원 카테고리)를 조합해 {@link Hospital} 테이블을 채운다.
 *
 * <p>두 단계로 나뉜다.
 * <ol>
 *   <li>1단계: 관광공사 목록을 순회하며 각 항목을 카카오에서 검색.
 *       매칭되면 source=TOURISM_API+KAKAO, 안 되면 관광공사 원본 데이터만으로
 *       source=TOURISM_API로 저장한다(이전에는 이 경우 그냥 버렸음).</li>
 *   <li>2단계: 관광공사 목록과 무관하게 서울 25개 구를 중심으로 카카오 병원 카테고리를
 *       전부 스윕해서, 1단계에서 이미 들어간 것과 겹치지 않는 새 병원을 source=KAKAO로 추가한다.</li>
 * </ol>
 */
@Service
public class HospitalSyncService {

    private static final Logger log = LoggerFactory.getLogger(HospitalSyncService.class);

    private static final String SOURCE_BOTH = "TOURISM_API+KAKAO";
    private static final String SOURCE_TOURISM_ONLY = "TOURISM_API";
    private static final String SOURCE_KAKAO_ONLY = "KAKAO";

    // 카카오 검색은 한글 상호명 기준이라, 관광공사 기본 언어(ENG)가 아니라 한글로 명시 요청한다.
    private static final String KOREAN_LANG = "KOR";
    private static final int DISTRICT_SWEEP_RADIUS_M = 5000;

    private final MedicalTourismService medicalTourismService;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final HospitalRepository hospitalRepository;

    public HospitalSyncService(
            MedicalTourismService medicalTourismService,
            KakaoPlaceClient kakaoPlaceClient,
            HospitalRepository hospitalRepository) {
        this.medicalTourismService = medicalTourismService;
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.hospitalRepository = hospitalRepository;
    }

    @Transactional
    public SyncResult sync() {
        Instant syncedAt = Instant.now();

        TourismPhaseResult tourismPhase = syncFromTourismApi(syncedAt);
        KakaoPhaseResult kakaoPhase = sweepKakaoOnly(syncedAt);

        log.info(
                "병원 동기화 완료: 관광공사={}건 (양쪽매칭={}, 관광공사단독={}), "
                        + "카카오 스윕={}건 발견 (카카오단독 신규={})",
                tourismPhase.fetched, tourismPhase.matchedBoth, tourismPhase.tourismOnly,
                kakaoPhase.sweepTotal, kakaoPhase.newlyCreated);

        return new SyncResult(
                tourismPhase.fetched,
                tourismPhase.matchedBoth,
                tourismPhase.tourismOnly,
                kakaoPhase.sweepTotal,
                kakaoPhase.newlyCreated,
                syncedAt);
    }

    private TourismPhaseResult syncFromTourismApi(Instant syncedAt) {
        List<MedicalTourismListItem> items = medicalTourismService.listAllSeoul(KOREAN_LANG);

        int matchedBoth = 0;
        int tourismOnly = 0;

        for (MedicalTourismListItem item : items) {
            String query = extractSearchQuery(item.title());
            Optional<KakaoPlace> match;
            try {
                match = kakaoPlaceClient.findHospital(query, item.mapX(), item.mapY());
            } catch (Exception e) {
                log.warn("카카오 검색 실패: title={}, error={}", item.title(), e.getMessage());
                match = Optional.empty();
            }

            String image = firstNonBlank(item.orgImage(), item.thumbImage());

            if (match.isPresent() && isHumanHospital(match.get())) {
                upsertMatched(item, match.get(), image, syncedAt);
                matchedBoth++;
            } else {
                upsertTourismOnly(item, image, syncedAt);
                tourismOnly++;
            }
        }
        return new TourismPhaseResult(items.size(), matchedBoth, tourismOnly);
    }

    private void upsertMatched(MedicalTourismListItem item, KakaoPlace place, String image, Instant syncedAt) {
        Hospital hospital = hospitalRepository.findByPlaceId(place.id()).orElse(null);
        if (hospital == null) {
            hospitalRepository.save(new Hospital(
                    place.id(),
                    item.contentId(),
                    place.placeName(),
                    place.categoryName(),
                    place.addressName(),
                    place.roadAddressName(),
                    place.mapX(),
                    place.mapY(),
                    image,
                    SOURCE_BOTH,
                    syncedAt));
        } else {
            hospital.updateFromSync(
                    place.placeName(),
                    place.categoryName(),
                    place.addressName(),
                    place.roadAddressName(),
                    place.mapX(),
                    place.mapY(),
                    image,
                    SOURCE_BOTH,
                    syncedAt);
        }
    }

    private void upsertTourismOnly(MedicalTourismListItem item, String image, Instant syncedAt) {
        BigDecimal mapX = parseOrNull(item.mapX());
        BigDecimal mapY = parseOrNull(item.mapY());
        if (mapX == null || mapY == null) {
            log.info("관광공사 좌표 없음, 스킵: title={}", item.title());
            return;
        }

        Hospital hospital = hospitalRepository.findByTourismContentId(item.contentId()).orElse(null);
        String address = firstNonBlank(item.baseAddr(), item.detailAddr());
        if (hospital == null) {
            hospitalRepository.save(new Hospital(
                    null,
                    item.contentId(),
                    item.title(),
                    null,
                    address,
                    null,
                    mapX,
                    mapY,
                    image,
                    SOURCE_TOURISM_ONLY,
                    syncedAt));
        } else {
            hospital.updateFromSync(
                    item.title(),
                    hospital.getCategoryName(),
                    address,
                    hospital.getRoadAddressName(),
                    mapX,
                    mapY,
                    image,
                    SOURCE_TOURISM_ONLY,
                    syncedAt);
        }
    }

    private KakaoPhaseResult sweepKakaoOnly(Instant syncedAt) {
        Map<String, KakaoPlace> deduped = new LinkedHashMap<>();
        for (SeoulDistricts.Center district : SeoulDistricts.ALL) {
            try {
                List<KakaoPlace> found = kakaoPlaceClient.sweepHospitals(
                        district.lat(), district.lng(), DISTRICT_SWEEP_RADIUS_M);
                for (KakaoPlace place : found) {
                    if (isHumanHospital(place)) {
                        deduped.putIfAbsent(place.id(), place);
                    }
                }
            } catch (Exception e) {
                log.warn("카카오 스윕 실패: district={}, error={}", district.name(), e.getMessage());
            }
        }

        int newlyCreated = 0;
        for (KakaoPlace place : deduped.values()) {
            if (hospitalRepository.findByPlaceId(place.id()).isPresent()) {
                continue; // 이미 1단계(관광공사 매칭)로 들어와 있음
            }
            hospitalRepository.save(new Hospital(
                    place.id(),
                    null,
                    place.placeName(),
                    place.categoryName(),
                    place.addressName(),
                    place.roadAddressName(),
                    place.mapX(),
                    place.mapY(),
                    null,
                    SOURCE_KAKAO_ONLY,
                    syncedAt));
            newlyCreated++;
        }
        return new KakaoPhaseResult(deduped.size(), newlyCreated);
    }

    /**
     * 카카오 HP8(병원) 카테고리에는 "가정,생활 > 반려동물 > 동물병원"도 섞여 나온다.
     * 사람 대상 의료기관("의료,건강 > ...")만 통과시킨다.
     */
    private static boolean isHumanHospital(KakaoPlace place) {
        String category = place.categoryName();
        return category != null && category.startsWith("의료,건강");
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
     * 관광공사 title은 "ATOP Plastic Surgery (에이탑 성형외과)"처럼 영문 상호명 뒤에
     * 괄호로 한글명이 붙는 경우가 많음. 카카오는 한글 상호명 기준 검색이 훨씬 잘 맞으므로
     * 마지막 괄호 안의 한글 부분을 추출해 검색어로 쓰고, 괄호가 없으면 원문 그대로 쓴다.
     */
    private static String extractSearchQuery(String title) {
        if (title == null) return null;
        int open = title.lastIndexOf('(');
        int close = title.lastIndexOf(')');
        if (open >= 0 && close > open) {
            String inner = title.substring(open + 1, close).trim();
            if (!inner.isEmpty()) return inner;
        }
        return title;
    }

    private record TourismPhaseResult(int fetched, int matchedBoth, int tourismOnly) {
    }

    private record KakaoPhaseResult(int sweepTotal, int newlyCreated) {
    }

    public record SyncResult(
            int tourismFetched,
            int matchedBoth,
            int tourismOnly,
            int kakaoSweepTotal,
            int kakaoOnlyNew,
            Instant syncedAt) {
    }
}
