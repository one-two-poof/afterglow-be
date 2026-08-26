package com.afterglow.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** TourAPI 4.0 지역기반 목록(areaBasedList2) 한 건 */
public record TourApiListItem(
        String contentId,
        String contentTypeId,
        String title,
        String addr1,
        String addr2,
        String tel,
        String firstImage,
        String mapX,
        String mapY,
        String sigunguCode,
        String cat1,
        String cat2,
        String cat3) {

    public static TourApiListItem from(JsonNode node) {
        return new TourApiListItem(
                node.path("contentid").asText(""),
                node.path("contenttypeid").asText(""),
                node.path("title").asText(""),
                node.path("addr1").asText(""),
                node.path("addr2").asText(""),
                node.path("tel").asText(""),
                node.path("firstimage").asText(""),
                node.path("mapx").asText(""),
                node.path("mapy").asText(""),
                node.path("sigungucode").asText(""),
                node.path("cat1").asText(""),
                node.path("cat2").asText(""),
                node.path("cat3").asText(""));
    }
}
