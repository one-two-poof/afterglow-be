package com.afterglow.service;

import com.afterglow.domain.Attraction;
import com.afterglow.domain.PlaceType;
import com.afterglow.kakao.KakaoPlace;
import com.afterglow.kakao.KakaoPlaceClient;
import com.afterglow.kakao.KakaoRatingClient;
import com.afterglow.kakao.SeoulDistricts;
import com.afterglow.repository.AttractionRepository;
import com.afterglow.web.dto.TourApiListItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
 * 관광공사 TourAPI 4.0(KorService2) 서울 전체 목록(관광지/문화시설/쇼핑)을 {@link Attraction}
 * 테이블에 채운다. {@link AccommodationSyncService}와 같은 패턴 — 카카오 카테고리가 있는 유형
 * (관광지/문화시설)은 카카오로 재검색해 매칭되면 기존 행을 그대로 갱신하고,
 * 카카오 대응 카테고리가 없는 유형(쇼핑)이나 매칭 실패 건은 tourism_content_id로 재동기화 매칭한다.
 *
 * <p>category_name은 매칭 성공 시 카카오의 ">" 계층 텍스트를 그대로 쓰고, 매칭 실패 시
 * {@link TourApiCategoryService}로 cat1/cat2/cat3 코드를 이름으로 풀어 같은 형식(">" 구분)으로 맞춘다.
 */
@Service
public class AttractionSyncService {

    private static final Logger log = LoggerFactory.getLogger(AttractionSyncService.class);

    private static final String SOURCE_BOTH = "TOURISM_API+KAKAO";
    private static final String SOURCE_TOURISM_ONLY = "TOURISM_API";
    private static final String SOURCE_KAKAO_ONLY = "KAKAO";

    /**
     * 카카오 카테고리 그룹 코드 대응이 없는 유형(null)은 매칭을 시도하지 않고 TOURISM_API 단독으로 저장한다.
     * 축제공연행사(15)와 음식점(39)은 TourAPI 응답이 최신화가 안 돼 있어(폐업/변경 반영 지연) 동기화
     * 대상에서 제외한다 (2026-08-26, 기존 행은 관리 페이지에서 수동 삭제함).
     */
    private static final List<CategoryConfig> CATEGORIES = List.of(
            new CategoryConfig(12, "관광지", "AT4"),
            new CategoryConfig(14, "문화시설", "CT1"),
            new CategoryConfig(38, "쇼핑", null));

    /** {@link AttractionClassifier}가 처리할 후보를 찾기 위한 카카오 카테고리 그룹 스윕(그룹코드 있는 것만). */
    private static final List<String> KAKAO_SWEEP_CATEGORY_GROUP_CODES = List.of("CE7", "CT1", "AT4");

    /** 카카오 전용 그룹코드가 없는 유형(가정,생활 트리) — "{구} {키워드}"로 텍스트 검색. */
    private static final List<String> SHOPPING_KEYWORDS = List.of("드럭스토어", "백화점", "쇼핑몰");

    /** 웰니스(찜질방/사우나/체형관리/피부관리)도 전용 그룹코드가 없어 텍스트 검색으로 찾는다. */
    private static final List<String> WELLNESS_KEYWORDS = List.of("찜질방", "사우나", "마사지", "스파");

    private final TourApiService tourApiService;
    private final TourApiCategoryService tourApiCategoryService;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final KakaoRatingClient kakaoRatingClient;
    private final AttractionClassifier attractionClassifier;
    private final AttractionRepository attractionRepository;
    private final PlaceTranslationService placeTranslationService;

    public AttractionSyncService(
            TourApiService tourApiService,
            TourApiCategoryService tourApiCategoryService,
            KakaoPlaceClient kakaoPlaceClient,
            KakaoRatingClient kakaoRatingClient,
            AttractionClassifier attractionClassifier,
            AttractionRepository attractionRepository,
            PlaceTranslationService placeTranslationService) {
        this.tourApiService = tourApiService;
        this.tourApiCategoryService = tourApiCategoryService;
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.kakaoRatingClient = kakaoRatingClient;
        this.attractionClassifier = attractionClassifier;
        this.attractionRepository = attractionRepository;
        this.placeTranslationService = placeTranslationService;
    }

    @Transactional
    public SyncResult sync() {
        Instant syncedAt = Instant.now();
        List<CategoryResult> results = new ArrayList<>();

        for (CategoryConfig category : CATEGORIES) {
            results.add(syncCategory(category, syncedAt));
        }

        KakaoSweepResult kakaoSweep = collectKakaoAttractions(syncedAt);

        log.info("관광명소 동기화 완료: {} / 카카오 스윕: {}", results, kakaoSweep);
        return new SyncResult(results, kakaoSweep, syncedAt);
    }

    private CategoryResult syncCategory(CategoryConfig category, Instant syncedAt) {
        List<TourApiListItem> items = tourApiService.listSeoulByContentType(category.contentTypeId());
        // 같은 목록을 언어별로 한 번 더 조회해 contentId→title 맵을 미리 만들어 둔다(건별 API 호출 방지).
        Map<String, String> jaTitles = tourApiService.titleByContentId(category.contentTypeId(), "ja");
        Map<String, String> enTitles = tourApiService.titleByContentId(category.contentTypeId(), "en");

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
                upsertMatched(item, match.get(), category, syncedAt, jaTitles, enTitles);
                matchedBoth++;
            } else {
                boolean saved = upsertTourismOnly(item, category, syncedAt, jaTitles, enTitles);
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

    /** contentId가 이번 목록에 있으면 TourAPI 공식 번역을 적용한다(자리만 만들고, 없으면 백필 스케줄러가 나중에 채움). */
    private void applyTourApiTranslations(
            Long attractionId, String contentId, Map<String, String> jaTitles, Map<String, String> enTitles) {
        if (contentId == null || contentId.isBlank()) {
            return;
        }
        placeTranslationService.applyPlaceName(PlaceType.ATTRACTION, attractionId, "ja", jaTitles.get(contentId), "TOURAPI_JPN");
        placeTranslationService.applyPlaceName(PlaceType.ATTRACTION, attractionId, "en", enTitles.get(contentId), "TOURAPI_ENG");
    }

    private void upsertMatched(
            TourApiListItem item,
            KakaoPlace kakaoPlace,
            CategoryConfig category,
            Instant syncedAt,
            Map<String, String> jaTitles,
            Map<String, String> enTitles) {
        Attraction attraction = attractionRepository.findByPlaceId(kakaoPlace.id()).orElse(null);
        String image = firstNonBlank(item.firstImage(), null);
        if (attraction == null) {
            attraction = new Attraction(
                    kakaoPlace.id(),
                    item.contentId(),
                    kakaoPlace.placeName(),
                    kakaoPlace.categoryName(),
                    kakaoPlace.addressName(),
                    kakaoPlace.mapX(),
                    kakaoPlace.mapY(),
                    image,
                    kakaoPlace.phone(),
                    kakaoPlace.placeUrl(),
                    SOURCE_BOTH,
                    syncedAt);
            attraction.applyMlTags(category.label(), null, null, null, null);
        } else {
            attraction.updateFromSync(
                    kakaoPlace.placeName(),
                    kakaoPlace.addressName(),
                    kakaoPlace.mapX(),
                    kakaoPlace.mapY(),
                    image,
                    kakaoPlace.phone(),
                    kakaoPlace.placeUrl(),
                    category.label(),
                    SOURCE_BOTH,
                    syncedAt);
        }
        applyWalkConstraintsIfMissing(attraction, category);
        attraction = attractionRepository.save(attraction);
        applyTourApiTranslations(attraction.getId(), item.contentId(), jaTitles, enTitles);
    }

    /** 카카오 매칭 실패/불가 — 관광공사 원본만으로 저장. 좌표가 없으면 스킵하고 false를 반환한다. */
    private boolean upsertTourismOnly(
            TourApiListItem item,
            CategoryConfig category,
            Instant syncedAt,
            Map<String, String> jaTitles,
            Map<String, String> enTitles) {
        BigDecimal mapX = parseOrNull(item.mapX());
        BigDecimal mapY = parseOrNull(item.mapY());
        if (mapX == null || mapY == null) {
            log.info("관광공사 좌표 없음, 스킵: category={}, title={}", category.label(), item.title());
            return false;
        }

        String address = firstNonBlank(item.addr1(), item.addr2());
        String hierarchicalCategoryName = firstNonBlank(
                tourApiCategoryService.resolveHierarchy(item.cat1(), item.cat2(), item.cat3()), category.label());
        Attraction attraction = attractionRepository.findByTourismContentId(item.contentId()).orElse(null);
        if (attraction == null) {
            attraction = new Attraction(
                    null,
                    item.contentId(),
                    item.title(),
                    hierarchicalCategoryName,
                    address,
                    mapX,
                    mapY,
                    firstNonBlank(item.firstImage(), null),
                    item.tel(),
                    null,
                    SOURCE_TOURISM_ONLY,
                    syncedAt);
            attraction.applyMlTags(category.label(), null, null, null, null);
        } else {
            attraction.updateFromSync(
                    item.title(),
                    address,
                    mapX,
                    mapY,
                    firstNonBlank(item.firstImage(), null),
                    firstNonBlank(item.tel(), attraction.getPhone()),
                    attraction.getPlaceUrl(),
                    category.label(),
                    SOURCE_TOURISM_ONLY,
                    syncedAt);
        }
        applyWalkConstraintsIfMissing(attraction, category);
        attraction = attractionRepository.save(attraction);
        applyTourApiTranslations(attraction.getId(), item.contentId(), jaTitles, enTitles);
        return true;
    }

    /**
     * 카카오 카테고리 그룹코드(CE7/CT1/AT4)·키워드(드럭스토어/백화점/쇼핑몰/찜질방/사우나/마사지/스파)로
     * 서울 25개 구를 훑어 {@link AttractionClassifier}로 분류되는 후보를 찾는다. TourAPI에 없는
     * 유형(카페·드럭스토어·백화점·쇼핑몰·찜질방·안마/스파 등)을 여기서 채운다.
     *
     * <p>이미 1단계(TourAPI 동기화)로 들어와 있는 행이면(같은 kakaoPlaceId) 새로 만들지 않고
     * primaryTypeName이 비어있을 때만 분류 결과를 얹는다. popularity 최소 기준이 있는 유형
     * (카페/미술관/공연장/찜질방/안마·스파)은 카카오맵 평점을 조회해 기준 미달이면 후보를 버린다 —
     * 평점 조회는 새로 생성되는 행에만 1회 수행한다(재동기화 시 재계산하지 않음, 호출량 절감).
     */
    private KakaoSweepResult collectKakaoAttractions(Instant syncedAt) {
        Map<String, KakaoPlace> discovered = new LinkedHashMap<>();

        for (SeoulDistricts.Center district : SeoulDistricts.ALL) {
            for (String categoryGroupCode : KAKAO_SWEEP_CATEGORY_GROUP_CODES) {
                sweepInto(discovered, district.name(), categoryGroupCode);
            }
            for (String keyword : SHOPPING_KEYWORDS) {
                sweepInto(discovered, district.name() + " " + keyword, null);
            }
            for (String keyword : WELLNESS_KEYWORDS) {
                sweepInto(discovered, district.name() + " " + keyword, null);
            }
        }

        int newlyCreated = 0;
        int merged = 0;
        int skippedUnmapped = 0;
        int skippedLowPopularity = 0;

        for (KakaoPlace place : discovered.values()) {
            Optional<AttractionClassifier.Classification> classified =
                    attractionClassifier.classify(place.categoryName(), place.placeName());
            if (classified.isEmpty()) {
                skippedUnmapped++;
                continue;
            }
            AttractionClassifier.Classification c = classified.get();

            Attraction existing = attractionRepository.findByPlaceId(place.id()).orElse(null);
            if (existing != null) {
                merged++;
                // 오늘 카카오 검색으로 다시 확인된 데이터이므로 이름/주소/좌표/source를 최신으로 갱신한다
                // (updateFromSync가 primaryTypeName은 write-once로 보호하므로 기존 분류는 안 건드림).
                // CSV_IMPORT처럼 더 이상 살아있는 소스가 없던 행이 이렇게 자연스럽게 정리된다.
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
                            c.primaryTypeName(),
                            SOURCE_KAKAO_ONLY,
                            syncedAt);
                }
                if (existing.getIsIndoor() == null) {
                    existing.applyWalkConstraints(
                            c.walkDefaults().isIndoor(),
                            c.walkDefaults().isHeatSource(),
                            c.walkDefaults().isMassageSpot(),
                            c.walkDefaults().walkHard());
                }
                continue;
            }

            Integer popularity = null;
            if (c.minPopularity() > 0) {
                int score = kakaoRatingClient.calculatePopularity(place.id());
                if (score < c.minPopularity()) {
                    skippedLowPopularity++;
                    continue;
                }
                popularity = score;
            }

            Attraction attraction = new Attraction(
                    place.id(),
                    null,
                    place.placeName(),
                    place.categoryName(),
                    place.addressName(),
                    place.mapX(),
                    place.mapY(),
                    null,
                    place.phone(),
                    place.placeUrl(),
                    SOURCE_KAKAO_ONLY,
                    syncedAt);
            attraction.applyMlTags(
                    c.primaryTypeName(),
                    c.walkDefaults().isIndoor(),
                    c.walkDefaults().isHeatSource(),
                    c.walkDefaults().isMassageSpot(),
                    c.walkDefaults().walkHard());
            attraction.applyPopularity(popularity);
            attractionRepository.save(attraction);
            newlyCreated++;
        }

        return new KakaoSweepResult(discovered.size(), newlyCreated, merged, skippedUnmapped, skippedLowPopularity);
    }

    private void sweepInto(Map<String, KakaoPlace> discovered, String query, String categoryGroupCode) {
        List<KakaoPlace> found;
        try {
            found = kakaoPlaceClient.searchKeywordAll(query, categoryGroupCode);
        } catch (Exception e) {
            log.warn("카카오 관광명소 스윕 실패: query={}, categoryGroupCode={}, error={}", query, categoryGroupCode, e.getMessage());
            return;
        }
        for (KakaoPlace place : found) {
            discovered.putIfAbsent(place.id(), place);
        }
    }

    /**
     * is_indoor 등 도보 제약 태그는 이미 값이 채워진 행(non-null)은 절대 건드리지 않고,
     * 아직 한 번도 분류되지 않은 행(null)에만 contentTypeId+텍스트 키워드 기반 규칙으로 채운다.
     */
    private void applyWalkConstraintsIfMissing(Attraction attraction, CategoryConfig category) {
        if (attraction.getIsIndoor() != null) {
            return;
        }
        WalkTags tags = classifyWalkTags(category.contentTypeId(), attraction.getPlaceName(), attraction.getCategoryName());
        attraction.applyWalkConstraints(tags.isIndoor(), tags.isHeatSource(), tags.isMassageSpot(), tags.walkHard());
    }

    /**
     * TourAPI/카카오 응답엔 실내 여부·찜질방 여부 같은 값이 없어서, contentTypeId별 기본값과
     * 장소명/카테고리명 키워드로 보수적으로 추정한다. 이 규칙은 is_indoor가 null인 행에만
     * 적용되므로, 다른 경로로 이미 채워진 값을 덮어쓰지 않는다.
     */
    private static WalkTags classifyWalkTags(int contentTypeId, String placeName, String categoryName) {
        String text = (nullToEmpty(placeName) + " " + nullToEmpty(categoryName));
        boolean heatSource = containsAny(text, "찜질방", "스파", "사우나", "온천", "한증막");
        boolean massageSpot = containsAny(text, "마사지", "안마", "맛사지");

        boolean indoor;
        int walkHard;
        switch (contentTypeId) {
            case 12 -> { // 관광지
                if (containsAny(text, "공원", "숲", "산", "하천", "둘레길", "생태", "광장", "정원")) {
                    indoor = false;
                    walkHard = 5;
                } else if (containsAny(text, "타워", "전망대", "박물관", "미술관", "전시관", "아쿠아리움")) {
                    indoor = true;
                    walkHard = 2;
                } else {
                    indoor = false;
                    walkHard = 4;
                }
            }
            case 14 -> { // 문화시설
                indoor = true;
                walkHard = containsAny(text, "영화관", "시네마") ? 1 : 2;
            }
            case 15 -> { // 축제공연행사
                if (containsAny(text, "공연장", "극장", "아트홀", "홀")) {
                    indoor = true;
                    walkHard = 1;
                } else {
                    indoor = false;
                    walkHard = 3;
                }
            }
            case 38 -> { // 쇼핑
                if (containsAny(text, "시장", "거리")) {
                    indoor = false;
                    walkHard = 3;
                } else {
                    indoor = true;
                    walkHard = 2;
                }
            }
            case 39 -> { // 음식점
                indoor = true;
                walkHard = 1;
            }
            default -> {
                indoor = false;
                walkHard = 3;
            }
        }
        return new WalkTags(indoor, heatSource, massageSpot, walkHard);
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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

    private record WalkTags(boolean isIndoor, boolean isHeatSource, boolean isMassageSpot, int walkHard) {
    }

    public record CategoryResult(
            int contentTypeId, String label, int fetched, int matchedBoth, int tourismOnly, int skippedNoCoords) {
    }

    public record KakaoSweepResult(
            int discovered, int newlyCreated, int merged, int skippedUnmapped, int skippedLowPopularity) {
    }

    public record SyncResult(List<CategoryResult> categories, KakaoSweepResult kakaoSweep, Instant syncedAt) {
    }
}
