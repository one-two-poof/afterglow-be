package com.afterglow.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 의료관광 목록(mdclTursmSyncList) 한 건 */
public record MedicalTourismListItem(
        String contentId,
        String title,
        String baseAddr,
        String detailAddr,
        String zipCd,
        String tel,
        String mapX,
        String mapY,
        String orgImage,
        String thumbImage,
        String langDivCd,
        String lDongRegnCd,
        String lDongSignguCd,
        String regDt,
        String mdfcnDt) {

    public static MedicalTourismListItem from(JsonNode node) {
        return new MedicalTourismListItem(
                node.path("contentId").asText(""),
                node.path("title").asText(""),
                node.path("baseAddr").asText(""),
                node.path("detailAddr").asText(""),
                node.path("zipCd").asText(""),
                node.path("tel").asText(""),
                node.path("mapX").asText(""),
                node.path("mapY").asText(""),
                node.path("orgImage").asText(""),
                node.path("thumbImage").asText(""),
                node.path("langDivCd").asText(""),
                node.path("lDongRegnCd").asText(""),
                node.path("lDongSignguCd").asText(""),
                node.path("regDt").asText(""),
                node.path("mdfcnDt").asText(""));
    }
}
