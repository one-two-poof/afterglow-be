package com.afterglow.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/** 카카오 로컬 API 키워드/카테고리 검색 결과 한 건 (documents[i]) */
public record KakaoPlace(
        String id,
        String placeName,
        String categoryName,
        String categoryGroupCode,
        String categoryGroupName,
        String phone,
        String addressName,
        String roadAddressName,
        String placeUrl,
        BigDecimal mapX,
        BigDecimal mapY) {

    public static KakaoPlace from(JsonNode doc) {
        return new KakaoPlace(
                doc.path("id").asText(""),
                doc.path("place_name").asText(""),
                doc.path("category_name").asText(""),
                doc.path("category_group_code").asText(""),
                doc.path("category_group_name").asText(""),
                doc.path("phone").asText(""),
                doc.path("address_name").asText(""),
                doc.path("road_address_name").asText(""),
                doc.path("place_url").asText(""),
                new BigDecimal(doc.path("x").asText("0")),
                new BigDecimal(doc.path("y").asText("0")));
    }
}
