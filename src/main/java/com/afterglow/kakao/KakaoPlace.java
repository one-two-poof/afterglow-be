package com.afterglow.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/** 카카오 로컬 API 키워드 검색 결과 한 건 (documents[i]) */
public record KakaoPlace(
        String id,
        String placeName,
        String categoryName,
        String addressName,
        String roadAddressName,
        BigDecimal mapX,
        BigDecimal mapY) {

    public static KakaoPlace from(JsonNode doc) {
        return new KakaoPlace(
                doc.path("id").asText(""),
                doc.path("place_name").asText(""),
                doc.path("category_name").asText(""),
                doc.path("address_name").asText(""),
                doc.path("road_address_name").asText(""),
                new BigDecimal(doc.path("x").asText("0")),
                new BigDecimal(doc.path("y").asText("0")));
    }
}
