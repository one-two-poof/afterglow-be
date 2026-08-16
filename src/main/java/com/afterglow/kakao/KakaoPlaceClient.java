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
        requireConfigured();
        if (!StringUtils.hasText(query)) {
            return Optional.empty();
        }

        boolean hasCoordinates = StringUtils.hasText(mapX) && StringUtils.hasText(mapY);

        JsonNode root = kakaoWebClient.get()
                .uri(builder -> {
                    builder.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("category_group_code", HOSPITAL_CATEGORY_GROUP_CODE)
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
     * 특정 좌표 중심 반경(m) 안의 병원 카테고리(HP8) 장소를 전부 훑는다.
     * 관광공사 목록과 무관하게, 카카오에만 있는 병원을 찾기 위한 용도.
     * 카카오 카테고리 검색은 페이지당 최대 15건, 최대 45페이지까지만 제공한다.
     */
    public List<KakaoPlace> sweepHospitals(double lat, double lng, int radiusM) {
        requireConfigured();
        List<KakaoPlace> results = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGE; page++) {
            int currentPage = page;
            JsonNode root = kakaoWebClient.get()
                    .uri(builder -> builder
                            .path("/v2/local/search/category.json")
                            .queryParam("category_group_code", HOSPITAL_CATEGORY_GROUP_CODE)
                            .queryParam("x", lng)
                            .queryParam("y", lat)
                            .queryParam("radius", radiusM)
                            .queryParam("page", currentPage)
                            .queryParam("size", PAGE_SIZE)
                            .build())
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
