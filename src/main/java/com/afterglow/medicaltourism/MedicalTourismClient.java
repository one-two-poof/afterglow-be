package com.afterglow.medicaltourism;

import com.afterglow.config.MedicalTourismProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class MedicalTourismClient {

    private final WebClient medicalTourismWebClient;
    private final MedicalTourismProperties properties;
    private final ObjectMapper objectMapper;

    public MedicalTourismClient(
            WebClient medicalTourismWebClient,
            MedicalTourismProperties properties,
            ObjectMapper objectMapper) {
        this.medicalTourismWebClient = medicalTourismWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 의료관광 기관 목록 조회 */
    public JsonNode fetchList(int pageNo, int numOfRows, String lang) {
        requireConfigured();

        String raw = medicalTourismWebClient.get()
                .uri(builder -> builder
                        .path("/mdclTursmSyncList")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("numOfRows", numOfRows)
                        .queryParam("pageNo", pageNo)
                        .queryParam("MobileOS", "ETC")
                        .queryParam("MobileApp", properties.mobileApp())
                        .queryParam("langDivCd", lang)
                        .queryParam("arrange", "A")
                        .queryParam("showflag", "1")
                        .queryParam("_type", "json")
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(String.class)
                .block();

        return parse(raw);
    }

    /** 특정 기관 상세 조회 */
    public JsonNode fetchDetail(String contentId, String lang) {
        requireConfigured();

        String raw = medicalTourismWebClient.get()
                .uri(builder -> builder
                        .path("/detailMdclTursm")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("numOfRows", 10)
                        .queryParam("pageNo", 1)
                        .queryParam("MobileOS", "ETC")
                        .queryParam("MobileApp", properties.mobileApp())
                        .queryParam("langDivCd", lang)
                        .queryParam("contentId", contentId)
                        .queryParam("_type", "json")
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(String.class)
                .block();

        return parse(raw);
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MedicalTourismApiException("공공데이터 API 응답이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            // _type=json이어도 서비스키 오류 등은 XML/HTML로 내려올 수 있음
            throw new MedicalTourismApiException(
                    "공공데이터 API 응답을 파싱할 수 없습니다: "
                            + raw.substring(0, Math.min(raw.length(), 500)));
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new MedicalTourismApiException(
                    "의료관광 API가 설정되지 않았습니다. medicaltourism.service-key를 지정하세요.");
        }
    }

    private Mono<? extends Throwable> mapError(
            org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(new MedicalTourismApiException(
                        "공공데이터 API 오류 " + response.statusCode() + ": " + body)));
    }

    public static class MedicalTourismApiException extends RuntimeException {
        public MedicalTourismApiException(String message) {
            super(message);
        }
    }
}
