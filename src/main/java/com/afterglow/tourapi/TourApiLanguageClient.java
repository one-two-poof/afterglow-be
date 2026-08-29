package com.afterglow.tourapi;

import com.afterglow.config.TourApiProperties;
import com.afterglow.tourapi.TourApiClient.TourApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * TourAPI 4.0의 EngService2/JpnService2 — KorService2({@link TourApiClient})와 endpoint/파라미터
 * 형태가 동일하고 base URL만 다르다(공공데이터포털에서 서비스별로 별도 활용신청 필요).
 * place_translations 백필 전용으로, 지역기반 목록(areaBasedList2)만 지원한다 — 같은 contentId로
 * 다시 목록을 조회하면 title이 요청 언어로 내려온다.
 */
@Component
public class TourApiLanguageClient {

    private final WebClient jpnWebClient;
    private final WebClient engWebClient;
    private final TourApiProperties properties;
    private final ObjectMapper objectMapper;

    public TourApiLanguageClient(
            @Qualifier("tourApiJpnWebClient") WebClient jpnWebClient,
            @Qualifier("tourApiEngWebClient") WebClient engWebClient,
            TourApiProperties properties,
            ObjectMapper objectMapper) {
        this.jpnWebClient = jpnWebClient;
        this.engWebClient = engWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** locale: "ja" 또는 "en". 미지원 locale이거나 서비스키 미설정이면 null. */
    public JsonNode fetchAreaBasedList(
            String locale, int contentTypeId, String areaCode, String sigunguCode, int pageNo, int numOfRows) {
        WebClient client = clientFor(locale);
        if (client == null || !properties.isConfigured()) {
            return null;
        }

        String raw = client.get()
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

    private WebClient clientFor(String locale) {
        if ("ja".equals(locale)) return jpnWebClient;
        if ("en".equals(locale)) return engWebClient;
        return null;
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new TourApiException("공공데이터 API 응답이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new TourApiException(
                    "공공데이터 API 응답을 파싱할 수 없습니다: " + raw.substring(0, Math.min(raw.length(), 500)));
        }
    }

    private Mono<? extends Throwable> mapError(
            org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(new TourApiException(
                        "공공데이터 API 오류 " + response.statusCode() + ": " + body)));
    }
}
