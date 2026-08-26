package com.afterglow.service;

import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.PlaceType;
import com.afterglow.kakao.KakaoPlace;
import com.afterglow.kakao.KakaoPlaceClient;
import com.afterglow.repository.HospitalAccommodationRepository;
import com.afterglow.web.dto.TourApiListItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관광공사 TourAPI 4.0(KorService2, contentTypeId=32 숙박) 강남구/서초구 목록을 카카오 로컬 API(AD5
 * 숙박 카테고리)와 매칭해 {@link HospitalAccommodation} 테이블의 ACCOMMODATION 행을 채운다.
 * {@link HospitalSyncService}의 관광공사+카카오 매칭 단계와 같은 패턴 — 매칭되면 기존 카카오 기반
 * (CSV_IMPORT 포함) 행을 그대로 찾아 갱신하므로 같은 숙소가 중복 생성되지 않는다.
 */
@Service
public class AccommodationSyncService {

    private static final Logger log = LoggerFactory.getLogger(AccommodationSyncService.class);

    private static final int ACCOMMODATION_CONTENT_TYPE_ID = 32;
    private static final String SOURCE_BOTH = "TOURISM_API+KAKAO";
    private static final String SOURCE_TOURISM_ONLY = "TOURISM_API";

    private final TourApiService tourApiService;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final HospitalAccommodationRepository hospitalAccommodationRepository;

    public AccommodationSyncService(
            TourApiService tourApiService,
            KakaoPlaceClient kakaoPlaceClient,
            HospitalAccommodationRepository hospitalAccommodationRepository) {
        this.tourApiService = tourApiService;
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.hospitalAccommodationRepository = hospitalAccommodationRepository;
    }

    @Transactional
    public SyncResult sync() {
        Instant syncedAt = Instant.now();
        List<TourApiListItem> items = tourApiService.listGangnamSeochoByContentType(ACCOMMODATION_CONTENT_TYPE_ID);

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
                upsertMatched(item, match.get(), syncedAt);
                matchedBoth++;
            } else {
                boolean saved = upsertTourismOnly(item, syncedAt);
                if (saved) {
                    tourismOnly++;
                } else {
                    skippedNoCoords++;
                }
            }
        }

        log.info(
                "숙소 동기화 완료: 관광공사={}건 (양쪽매칭={}, 관광공사단독={}, 좌표없음스킵={})",
                items.size(), matchedBoth, tourismOnly, skippedNoCoords);

        return new SyncResult(items.size(), matchedBoth, tourismOnly, skippedNoCoords, syncedAt);
    }

    private void upsertMatched(TourApiListItem item, KakaoPlace kakaoPlace, Instant syncedAt) {
        HospitalAccommodation place = hospitalAccommodationRepository.findByPlaceId(kakaoPlace.id()).orElse(null);
        String image = firstNonBlank(item.firstImage(), null);
        if (place == null) {
            hospitalAccommodationRepository.save(new HospitalAccommodation(
                    kakaoPlace.id(),
                    item.contentId(),
                    kakaoPlace.placeName(),
                    kakaoPlace.categoryGroupName(),
                    kakaoPlace.addressName(),
                    kakaoPlace.roadAddressName(),
                    kakaoPlace.mapX(),
                    kakaoPlace.mapY(),
                    image,
                    kakaoPlace.categoryGroupCode(),
                    kakaoPlace.categoryGroupName(),
                    kakaoPlace.phone(),
                    kakaoPlace.placeUrl(),
                    PlaceType.ACCOMMODATION,
                    SOURCE_BOTH,
                    syncedAt));
        } else {
            place.updateFromSync(
                    kakaoPlace.placeName(),
                    kakaoPlace.categoryGroupName(),
                    kakaoPlace.addressName(),
                    kakaoPlace.roadAddressName(),
                    kakaoPlace.mapX(),
                    kakaoPlace.mapY(),
                    image,
                    kakaoPlace.categoryGroupCode(),
                    kakaoPlace.categoryGroupName(),
                    kakaoPlace.phone(),
                    kakaoPlace.placeUrl(),
                    SOURCE_BOTH,
                    syncedAt);
        }
    }

    /** 카카오 매칭 실패 — 관광공사 원본만으로 저장. 좌표가 없으면 스킵하고 false를 반환한다. */
    private boolean upsertTourismOnly(TourApiListItem item, Instant syncedAt) {
        BigDecimal mapX = parseOrNull(item.mapX());
        BigDecimal mapY = parseOrNull(item.mapY());
        if (mapX == null || mapY == null) {
            log.info("관광공사 좌표 없음, 스킵: title={}", item.title());
            return false;
        }

        String address = firstNonBlank(item.addr1(), item.addr2());
        HospitalAccommodation place = hospitalAccommodationRepository.findByTourismContentId(item.contentId()).orElse(null);
        if (place == null) {
            hospitalAccommodationRepository.save(new HospitalAccommodation(
                    null,
                    item.contentId(),
                    item.title(),
                    "숙박",
                    address,
                    null,
                    mapX,
                    mapY,
                    firstNonBlank(item.firstImage(), null),
                    String.valueOf(ACCOMMODATION_CONTENT_TYPE_ID),
                    "숙박",
                    item.tel(),
                    null,
                    PlaceType.ACCOMMODATION,
                    SOURCE_TOURISM_ONLY,
                    syncedAt));
        } else {
            place.updateFromSync(
                    item.title(),
                    place.getCategoryName(),
                    address,
                    place.getRoadAddressName(),
                    mapX,
                    mapY,
                    firstNonBlank(item.firstImage(), null),
                    place.getCategoryGroupCode(),
                    place.getCategoryGroupName(),
                    firstNonBlank(item.tel(), place.getPhone()),
                    place.getPlaceUrl(),
                    SOURCE_TOURISM_ONLY,
                    syncedAt);
        }
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

    public record SyncResult(
            int tourismFetched,
            int matchedBoth,
            int tourismOnly,
            int skippedNoCoords,
            Instant syncedAt) {
    }
}
