package com.afterglow.tourapi;

import com.afterglow.config.TourApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TourApiClient {

    private final WebClient tourApiWebClient;
    private final TourApiProperties properties;
    private final ObjectMapper objectMapper;

    public TourApiClient(WebClient tourApiWebClient, TourApiProperties properties, ObjectMapper objectMapper) {
        this.tourApiWebClient = tourApiWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 지역 기반 관광정보 목록 조회 (areaBasedList2). areaCode=1은 서울, sigunguCode로 구/군까지 좁힘. */
    public JsonNode fetchAreaBasedList(int contentTypeId, String areaCode, String sigunguCode, int pageNo, int numOfRows) {
        requireConfigured();

        String raw = tourApiWebClient.get()
                .uri(builder -> builder
                        .path("/areaBasedList2")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("numOfRows", numOfRows)
                        .queryParam("pageNo", pageNo)
                        .queryParam("MobileOS", "ETC")
                        .queryParam("MobileApp", properties.mobileApp())
                        .queryParam("arrange", "A")
                        .queryParam("contentTypeId", contentTypeId)
                        .queryParam("areaCode", areaCode)
                        .queryParam("sigunguCode", sigunguCode)
                        .queryParam("_type", "json")
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(String.class)
                .block();

        return parse(raw);
    }

    /**
     * 분류체계 코드 조회 (categoryCode2). cat1 없이 호출하면 대분류 전체, cat1만 주면 그 아래 중분류
     * 전체, cat1+cat2를 주면 그 아래 소분류 전체를 반환한다.
     */
    public JsonNode fetchCategoryCode(String cat1, String cat2) {
        requireConfigured();

        String raw = tourApiWebClient.get()
                .uri(builder -> {
                    builder.path("/categoryCode2")
                            .queryParam("serviceKey", properties.serviceKey())
                            .queryParam("numOfRows", 50)
                            .queryParam("pageNo", 1)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", properties.mobileApp())
                            .queryParam("_type", "json");
                    if (cat1 != null && !cat1.isBlank()) {
                        builder.queryParam("cat1", cat1);
                    }
                    if (cat2 != null && !cat2.isBlank()) {
                        builder.queryParam("cat2", cat2);
                    }
                    return builder.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(String.class)
                .block();

        return parse(raw);
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new TourApiException("공공데이터 API 응답이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            // _type=json이어도 서비스키 오류 등은 XML/HTML로 내려올 수 있음
            throw new TourApiException(
                    "공공데이터 API 응답을 파싱할 수 없습니다: " + raw.substring(0, Math.min(raw.length(), 500)));
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new TourApiException("관광정보 API가 설정되지 않았습니다. tourapi.service-key를 지정하세요.");
        }
    }

    private Mono<? extends Throwable> mapError(
            org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(new TourApiException(
                        "공공데이터 API 오류 " + response.statusCode() + ": " + body)));
    }

    public static class TourApiException extends RuntimeException {
        public TourApiException(String message) {
            super(message);
        }
    }
}
