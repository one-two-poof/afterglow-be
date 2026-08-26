package com.afterglow.service;

import com.afterglow.domain.Attraction;
import com.afterglow.kakao.KakaoPlace;
import com.afterglow.kakao.KakaoPlaceClient;
import com.afterglow.repository.AttractionRepository;
import com.afterglow.web.dto.TourApiListItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관광공사 TourAPI 4.0(KorService2) 강남구/서초구 목록 5개 카테고리(관광지/문화시설/축제공연행사/쇼핑/음식점)를
 * {@link Attraction} 테이블에 채운다. {@link AccommodationSyncService}와 같은 패턴 —
 * 카카오 카테고리가 있는 유형(관광지/문화시설/음식점)은 카카오로 재검색해 매칭되면 기존 행(카카오 CSV
 * import 포함)을 그대로 갱신하고, 카카오 대응 카테고리가 없는 유형(축제공연행사/쇼핑)이나 매칭 실패 건은
 * tourism_content_id로 재동기화 매칭한다.
 *
 * <p>attractions 테이블에는 카테고리 구분 전용 컬럼이 없어서, contentTypeId를 category_group_code에
 * 항상 강제로 저장한다(매칭 성공 시에도 카카오의 세부 코드 대신 이 값을 쓴다).
 */
@Service
public class AttractionSyncService {

    private static final Logger log = LoggerFactory.getLogger(AttractionSyncService.class);

    private static final String SOURCE_BOTH = "TOURISM_API+KAKAO";
    private static final String SOURCE_TOURISM_ONLY = "TOURISM_API";

    /** 카카오 카테고리 그룹 코드 대응이 없는 유형(null)은 매칭을 시도하지 않고 TOURISM_API 단독으로 저장한다. */
    private static final List<CategoryConfig> CATEGORIES = List.of(
            new CategoryConfig(12, "관광지", "AT4"),
            new CategoryConfig(14, "문화시설", "CT1"),
            new CategoryConfig(15, "축제공연행사", null),
            new CategoryConfig(38, "쇼핑", null),
            new CategoryConfig(39, "음식점", "FD6"));

    private final TourApiService tourApiService;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final AttractionRepository attractionRepository;

    public AttractionSyncService(
            TourApiService tourApiService,
            KakaoPlaceClient kakaoPlaceClient,
            AttractionRepository attractionRepository) {
        this.tourApiService = tourApiService;
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.attractionRepository = attractionRepository;
    }

    @Transactional
    public SyncResult sync() {
        Instant syncedAt = Instant.now();
        List<CategoryResult> results = new ArrayList<>();

        for (CategoryConfig category : CATEGORIES) {
            results.add(syncCategory(category, syncedAt));
        }

        log.info("관광명소 동기화 완료: {}", results);
        return new SyncResult(results, syncedAt);
    }

    private CategoryResult syncCategory(CategoryConfig category, Instant syncedAt) {
        List<TourApiListItem> items = tourApiService.listGangnamSeochoByContentType(category.contentTypeId());

        int matchedBoth = 0;
        int tourismOnly = 0;
        int skippedNoCoords = 0;

        for (TourApiListItem item : items) {
            Optional<KakaoPlace> match = Optional.empty();
            if (category.kakaoCategoryGroupCode() != null) {
                String query = extractSearchQuery(item.title());
                try {
                    match = kakaoPlaceClient.findByCategory(
                            query, category.kakaoCategoryGroupCode(), item.mapX(), item.mapY());
                } catch (Exception e) {
                    log.warn("카카오 검색 실패: title={}, error={}", item.title(), e.getMessage());
                }
            }

            if (match.isPresent()) {
                upsertMatched(item, match.get(), category, syncedAt);
                matchedBoth++;
            } else {
                boolean saved = upsertTourismOnly(item, category, syncedAt);
                if (saved) {
                    tourismOnly++;
                } else {
                    skippedNoCoords++;
                }
            }
        }

        return new CategoryResult(
                category.contentTypeId(), category.label(), items.size(), matchedBoth, tourismOnly, skippedNoCoords);
    }

    private void upsertMatched(TourApiListItem item, KakaoPlace kakaoPlace, CategoryConfig category, Instant syncedAt) {
        Attraction attraction = attractionRepository.findByPlaceId(kakaoPlace.id()).orElse(null);
        String contentTypeId = String.valueOf(category.contentTypeId());
        String image = firstNonBlank(item.firstImage(), null);
        if (attraction == null) {
            Attraction created = new Attraction(
                    kakaoPlace.id(),
                    item.contentId(),
                    kakaoPlace.placeName(),
                    kakaoPlace.categoryName(),
                    kakaoPlace.addressName(),
                    kakaoPlace.roadAddressName(),
                    kakaoPlace.mapX(),
                    kakaoPlace.mapY(),
                    image,
                    contentTypeId,
                    category.label(),
                    kakaoPlace.phone(),
                    kakaoPlace.placeUrl(),
                    SOURCE_BOTH,
                    syncedAt);
            created.applyMlTags(null, category.label(), null, null, null, null, null, null);
            attractionRepository.save(created);
        } else {
            attraction.updateFromSync(
                    kakaoPlace.placeName(),
                    kakaoPlace.categoryName(),
                    kakaoPlace.addressName(),
                    kakaoPlace.roadAddressName(),
                    kakaoPlace.mapX(),
                    kakaoPlace.mapY(),
                    image,
                    contentTypeId,
                    category.label(),
                    kakaoPlace.phone(),
                    kakaoPlace.placeUrl(),
                    category.label(),
                    SOURCE_BOTH,
                    syncedAt);
        }
    }

    /** 카카오 매칭 실패/불가 — 관광공사 원본만으로 저장. 좌표가 없으면 스킵하고 false를 반환한다. */
    private boolean upsertTourismOnly(TourApiListItem item, CategoryConfig category, Instant syncedAt) {
        BigDecimal mapX = parseOrNull(item.mapX());
        BigDecimal mapY = parseOrNull(item.mapY());
        if (mapX == null || mapY == null) {
            log.info("관광공사 좌표 없음, 스킵: category={}, title={}", category.label(), item.title());
            return false;
        }

        String contentTypeId = String.valueOf(category.contentTypeId());
        String address = firstNonBlank(item.addr1(), item.addr2());
        Attraction attraction = attractionRepository.findByTourismContentId(item.contentId()).orElse(null);
        if (attraction == null) {
            Attraction created = new Attraction(
                    null,
                    item.contentId(),
                    item.title(),
                    category.label(),
                    address,
                    null,
                    mapX,
                    mapY,
                    firstNonBlank(item.firstImage(), null),
                    contentTypeId,
                    category.label(),
                    item.tel(),
                    null,
                    SOURCE_TOURISM_ONLY,
                    syncedAt);
            created.applyMlTags(null, category.label(), null, null, null, null, null, null);
            attractionRepository.save(created);
        } else {
            attraction.updateFromSync(
                    item.title(),
                    category.label(),
                    address,
                    attraction.getRoadAddressName(),
                    mapX,
                    mapY,
                    firstNonBlank(item.firstImage(), null),
                    contentTypeId,
                    category.label(),
                    firstNonBlank(item.tel(), attraction.getPhone()),
                    attraction.getPlaceUrl(),
                    category.label(),
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
     * TourAPI title은 "강남스테이힐(Gangnam Stay Hill)"처럼 한글 상호명 뒤에 괄호로 영문명이
     * 붙는 경우가 많음. 카카오는 한글 상호명 기준 검색이 잘 맞으므로 괄호 앞부분을 검색어로 쓰고,
     * 괄호가 없으면 원문 그대로 쓴다.
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

    private record CategoryConfig(int contentTypeId, String label, String kakaoCategoryGroupCode) {
    }

    public record CategoryResult(
            int contentTypeId, String label, int fetched, int matchedBoth, int tourismOnly, int skippedNoCoords) {
    }

    public record SyncResult(List<CategoryResult> categories, Instant syncedAt) {
    }
}
