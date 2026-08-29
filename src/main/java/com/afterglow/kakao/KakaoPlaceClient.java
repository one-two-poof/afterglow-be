package com.afterglow.kakao;

import com.afterglow.config.KakaoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class KakaoPlaceClient {

    /** 카카오 로컬 카테고리 코드 — 병원 (성형외과/피부과/한의원 등 의료기관 포함) */
    private static final String HOSPITAL_CATEGORY_GROUP_CODE = "HP8";
    /** 카카오 로컬 카테고리 코드 — 숙박 */
    private static final String ACCOMMODATION_CATEGORY_GROUP_CODE = "AD5";
    private static final int SEARCH_RADIUS_M = 2000;
    private static final int PAGE_SIZE = 15;
    private static final int MAX_PAGE = 45; // 카카오 API 자체 상한

    private final WebClient kakaoWebClient;
    private final KakaoProperties properties;

    public KakaoPlaceClient(WebClient kakaoWebClient, KakaoProperties properties) {
        this.kakaoWebClient = kakaoWebClient;
        this.properties = properties;
    }

    /**
     * 병원 카테고리(HP8)로 제한해서 키워드 검색. mapX/mapY가 있으면 그 좌표 기준
     * 반경 내에서 거리순으로 가장 가까운 1건을 반환한다.
     */
    public Optional<KakaoPlace> findHospital(String query, String mapX, String mapY) {
        return findByCategory(query, HOSPITAL_CATEGORY_GROUP_CODE, mapX, mapY);
    }

    /** 병원 카테고리(HP8)로 제한한 키워드 검색 전체 결과 — {@link #searchKeywordAll} 참고. */
    public List<KakaoPlace> searchHospitalKeywordAll(String query) {
        return searchKeywordAll(query, HOSPITAL_CATEGORY_GROUP_CODE);
    }

    /**
     * 숙박 카테고리(AD5)로 제한해서 키워드 검색. mapX/mapY가 있으면 그 좌표 기준
     * 반경 내에서 거리순으로 가장 가까운 1건을 반환한다.
     */
    public Optional<KakaoPlace> findAccommodation(String query, String mapX, String mapY) {
        return findByCategory(query, ACCOMMODATION_CATEGORY_GROUP_CODE, mapX, mapY);
    }

    /**
     * 임의의 카카오 카테고리 그룹 코드로 제한해서 키워드 검색. mapX/mapY가 있으면 그 좌표 기준
     * 반경 내에서 거리순으로 가장 가까운 1건을 반환한다. 관광지(AT4)/문화시설(CT1)/음식점(FD6)처럼
     * 전용 헬퍼가 없는 카테고리를 매칭할 때 직접 쓴다.
     */
    public Optional<KakaoPlace> findByCategory(String query, String categoryGroupCode, String mapX, String mapY) {
        requireConfigured();
        if (!StringUtils.hasText(query)) {
            return Optional.empty();
        }

        boolean hasCoordinates = StringUtils.hasText(mapX) && StringUtils.hasText(mapY);

        JsonNode root = kakaoWebClient.get()
                .uri(builder -> {
                    builder.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("category_group_code", categoryGroupCode)
                            .queryParam("size", 1);
                    if (hasCoordinates) {
                        builder.queryParam("x", mapX)
                                .queryParam("y", mapY)
                                .queryParam("radius", SEARCH_RADIUS_M)
                                .queryParam("sort", "distance");
                    }
                    return builder.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(JsonNode.class)
                .block();

        JsonNode documents = root != null ? root.path("documents") : null;
        if (documents == null || !documents.isArray() || documents.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(KakaoPlace.from(documents.get(0)));
    }

    /**
     * 키워드 검색(좌표/반경 없이 텍스트 쿼리만)으로 카테고리 그룹 코드 내 결과를 전부 훑는다.
     * "강남구 리프팅"처럼 지역명+시술명을 쿼리에 합쳐 넣는 방식으로 씀 — 좌표 기반 반경 검색이 아니라
     * 카카오 자체 키워드 관련도 순으로 나온다. 페이지당 최대 15건, 최대 45페이지까지만 제공한다.
     * categoryGroupCode가 null이면 그룹 제한 없이 검색한다("가정,생활" 트리처럼 전용 그룹코드가 없는 유형).
     */
    public List<KakaoPlace> searchKeywordAll(String query, String categoryGroupCode) {
        requireConfigured();
        List<KakaoPlace> results = new ArrayList<>();
        if (!StringUtils.hasText(query)) {
            return results;
        }

        for (int page = 1; page <= MAX_PAGE; page++) {
            int currentPage = page;
            JsonNode root = kakaoWebClient.get()
                    .uri(builder -> {
                        builder.path("/v2/local/search/keyword.json")
                                .queryParam("query", query)
                                .queryParam("page", currentPage)
                                .queryParam("size", PAGE_SIZE);
                        if (StringUtils.hasText(categoryGroupCode)) {
                            builder.queryParam("category_group_code", categoryGroupCode);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::mapError)
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode documents = root != null ? root.path("documents") : null;
            if (documents == null || !documents.isArray() || documents.isEmpty()) {
                break;
            }
            documents.forEach(doc -> results.add(KakaoPlace.from(doc)));

            boolean isEnd = root.path("meta").path("is_end").asBoolean(true);
            if (isEnd) {
                break;
            }
        }
        return results;
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new KakaoApiException("카카오 API가 설정되지 않았습니다. kakao.rest-api-key를 지정하세요.");
        }
    }

    private Mono<? extends Throwable> mapError(
            org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(
                        new KakaoApiException("카카오 API 오류 " + response.statusCode() + ": " + body)));
    }

    public static class KakaoApiException extends RuntimeException {
        public KakaoApiException(String message) {
            super(message);
        }
    }
}
