package com.afterglow.service;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 카카오 {@code categoryName}(">" 구분 계층 경로)과 장소명으로 관광명소 후보를 분류한다.
 * 코드 구현용 정규 매핑 표(2026-08-30 확정본)를 그대로 옮긴 것 — 순서대로 검사해 첫 매칭을 쓴다.
 * 제외 규칙이 허용 규칙보다 항상 우선한다. 매칭되는 게 없으면(해당없음) {@link Optional#empty()}.
 *
 * <p>표에서 정한 그대로:
 * <ul>
 *   <li>병원(HP8, "의료,건강" 경로)·숙소("여행 > 숙박" 경로)는 이 분류기의 대상이 아니다
 *       ({@link HospitalSyncService}/{@link AccommodationSyncService}가 별도로 처리) — D01/D02.</li>
 *   <li>카페(C01)/미술관·공연장(M01,M02)/찜질방·안마(W01~W03)는 popularity 최소 기준을 넘어야
 *       채택된다(미달이면 후보 자체를 버림). 나머지는 popularity와 무관하게 채택.</li>
 *   <li>REVIEW 표시가 붙었던 항목(쇼핑몰 S03/S04, 복합문화공간 M07)은 지금은 별도 검수 큐가 없어
 *       그냥 자동 채택한다.</li>
 * </ul>
 */
@Component
public class AttractionClassifier {

    public record WalkDefaults(boolean isIndoor, boolean isHeatSource, boolean isMassageSpot, int walkHard) {
    }

    public record Classification(String primaryTypeName, WalkDefaults walkDefaults, int minPopularity) {
    }

    private static final WalkDefaults CAFE_OR_CINEMA = new WalkDefaults(true, false, false, 1);
    private static final WalkDefaults SAUNA = new WalkDefaults(true, true, false, 1);
    private static final WalkDefaults BODY_CARE_SPA = new WalkDefaults(true, false, true, 1);
    private static final WalkDefaults SKIN_CARE_SPA = new WalkDefaults(true, true, true, 1);
    private static final WalkDefaults LIGHT_INDOOR = new WalkDefaults(true, false, false, 2); // 드럭스토어/공연장/문화원
    private static final WalkDefaults GALLERY = new WalkDefaults(true, false, false, 3); // 미술관/박물관
    private static final WalkDefaults OUTDOOR_EASY = new WalkDefaults(false, false, false, 3); // 공원/거리
    private static final WalkDefaults LARGE_INDOOR = new WalkDefaults(true, false, false, 4); // 백화점/쇼핑몰/복합문화공간
    private static final WalkDefaults NATURE = new WalkDefaults(false, false, false, 5); // 산/섬/숲

    private static final int NO_POPULARITY_GATE = 0;

    public Optional<Classification> classify(String categoryName, String placeName) {
        if (categoryName == null || categoryName.isBlank()) {
            return Optional.empty();
        }
        // D01/D02 — 병원/숙소는 이 분류기 대상이 아니다(별도 동기화가 처리).
        if (categoryName.startsWith("의료,건강") || categoryName.startsWith("여행 > 숙박")) {
            return Optional.empty();
        }

        Optional<Classification> cafe = classifyCafe(categoryName, placeName);
        if (cafe.isPresent()) return cafe;

        Optional<Classification> shopping = classifyShopping(categoryName);
        if (shopping.isPresent()) return shopping;

        Optional<Classification> culture = classifyCulture(categoryName, placeName);
        if (culture.isPresent()) return culture;

        Optional<Classification> wellness = classifyWellness(categoryName);
        if (wellness.isPresent()) return wellness;

        return classifyTravel(categoryName);
    }

    // ── C01 카페 ─────────────────────────────────────────────────────────
    private Optional<Classification> classifyCafe(String categoryName, String placeName) {
        boolean isCafe = categoryName.startsWith("음식점 > 카페") || hasSegment(categoryName, "카페");
        if (!isCafe) {
            return Optional.empty();
        }
        if (nameContainsAny(placeName, "북카페", "만화카페", "애견카페", "고양이카페", "키즈카페", "방탈출카페")) {
            return Optional.empty();
        }
        return Optional.of(new Classification("카페", CAFE_OR_CINEMA, 5));
    }

    // ── S01~S05 카페·쇼핑 ────────────────────────────────────────────────
    private Optional<Classification> classifyShopping(String categoryName) {
        if (nameContainsAny(categoryName, "상가,아케이드", "쇼핑시설관리운영", "인터넷쇼핑몰", "주차장", "고객센터", "문화센터", "면세점")) {
            return Optional.empty();
        }
        if (categoryName.startsWith("가정,생활 > 드럭스토어")) {
            return Optional.of(new Classification("드럭스토어", LIGHT_INDOOR, NO_POPULARITY_GATE));
        }
        if (categoryName.startsWith("가정,생활 > 백화점") || hasSegment(categoryName, "백화점")) {
            return Optional.of(new Classification("백화점", LARGE_INDOOR, NO_POPULARITY_GATE));
        }
        if (categoryName.startsWith("가정,생활 > 복합쇼핑몰")
                || categoryName.startsWith("가정,생활 > 쇼핑몰")
                || categoryName.startsWith("가정,생활 > 상설할인매장")) {
            return Optional.of(new Classification("쇼핑몰", LARGE_INDOOR, NO_POPULARITY_GATE));
        }
        return Optional.empty();
    }

    // ── M01~M07 문화시설 ─────────────────────────────────────────────────
    private Optional<Classification> classifyCulture(String categoryName, String placeName) {
        if (!categoryName.startsWith("문화,예술")) {
            return Optional.empty();
        }
        if (nameContainsAny(categoryName + " " + nullToEmpty(placeName),
                "독서실", "웨딩", "컨벤션", "골프장", "연구소", "사무실", "대관", "정원", "쇼핑시설")) {
            return Optional.empty();
        }

        if (hasSegment(categoryName, "미술관", "전시관", "전시시설")) {
            return Optional.of(new Classification("미술관", GALLERY, 3));
        }
        if (hasSegment(categoryName, "공연장,연극극장", "공연장", "연극극장", "아트홀", "소극장")) {
            return Optional.of(new Classification("공연장", LIGHT_INDOOR, 3));
        }
        if (hasSegment(categoryName, "박물관")) {
            return Optional.of(new Classification("박물관", GALLERY, NO_POPULARITY_GATE));
        }
        if (categoryName.startsWith("문화,예술 > 영화,영상 > 영화관") || hasSegment(categoryName, "영화관")) {
            return Optional.of(new Classification("영화관", CAFE_OR_CINEMA, NO_POPULARITY_GATE));
        }
        if (hasSegment(categoryName, "문화원", "기념관")) {
            return Optional.of(new Classification("문화원", LIGHT_INDOOR, NO_POPULARITY_GATE));
        }
        if (hasSegment(categoryName, "아쿠아리움")) {
            return Optional.of(new Classification("복합문화공간", LARGE_INDOOR, NO_POPULARITY_GATE));
        }
        if (hasSegment(categoryName, "문화시설")
                && nameContainsAny(placeName, "복합문화", "문화공간", "문화예술공간")) {
            return Optional.of(new Classification("복합문화공간", LARGE_INDOOR, NO_POPULARITY_GATE));
        }
        return Optional.empty();
    }

    // ── W01~W03 웰니스 ───────────────────────────────────────────────────
    private Optional<Classification> classifyWellness(String categoryName) {
        if (hasSegment(categoryName, "찜질방", "사우나", "목욕탕")) {
            return Optional.of(new Classification("찜질방/사우나", SAUNA, 4));
        }
        if (hasSegment(categoryName, "체형관리")) {
            return Optional.of(new Classification("안마/스파", BODY_CARE_SPA, 4));
        }
        if (hasSegment(categoryName, "피부관리")) {
            return Optional.of(new Classification("안마/스파", SKIN_CARE_SPA, 4));
        }
        return Optional.empty();
    }

    // ── T01~T11 여행/자연 ────────────────────────────────────────────────
    private Optional<Classification> classifyTravel(String categoryName) {
        if (categoryName.startsWith("여행 > 공원") || categoryName.startsWith("여행 > 관광,명소 > 수목원,식물원")) {
            return Optional.of(new Classification("공원", OUTDOOR_EASY, NO_POPULARITY_GATE));
        }
        if (categoryName.startsWith("여행 > 관광,명소 > 도보여행") || categoryName.startsWith("여행 > 관광,명소 > 테마거리")) {
            return Optional.of(new Classification("거리", OUTDOOR_EASY, NO_POPULARITY_GATE));
        }
        if (categoryName.startsWith("여행 > 관광,명소 > 산")
                || categoryName.startsWith("여행 > 관광,명소 > 섬")
                || categoryName.startsWith("여행 > 관광,명소 > 숲")) {
            return Optional.of(new Classification("자연", NATURE, NO_POPULARITY_GATE));
        }
        if (categoryName.startsWith("여행 > 관광,명소 > 테마파크")) {
            return Optional.of(new Classification("복합문화공간", LARGE_INDOOR, NO_POPULARITY_GATE));
        }
        return Optional.empty();
    }

    private static boolean hasSegment(String categoryName, String... segments) {
        for (String part : categoryName.split(" > ")) {
            for (String seg : segments) {
                if (part.equals(seg)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean nameContainsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
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
}
