package com.afterglow.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 의료관광 상세(detailMdclTursm) */
public record MedicalTourismDetail(
        String contentId,
        String mdclTursmDivInfo,
        String insttDevInfo,
        String mainMdlcSubjInfo,
        String specProcMdlcInfo,
        String svcLangInfo,
        String hmpgInfo,
        String prSnsInfo,
        String histrCn,
        String onlineRsvtPsblYn,
        String gdsCnselCn,
        String coorResidYn,
        String specFcltyInfo,
        String corprHsptlInfo,
        String trtmntGdsKndInfo) {

    public static MedicalTourismDetail from(JsonNode node) {
        return new MedicalTourismDetail(
                node.path("contentId").asText(""),
                node.path("mdclTursmDivInfo").asText(""),
                node.path("insttDevInfo").asText(""),
                node.path("mainMdlcSubjInfo").asText(""),
                node.path("specProcMdlcInfo").asText(""),
                node.path("svcLangInfo").asText(""),
                node.path("hmpgInfo").asText(""),
                node.path("prSnsInfo").asText(""),
                node.path("histrCn").asText(""),
                node.path("onlineRsvtPsblYn").asText(""),
                node.path("gdsCnselCn").asText(""),
                node.path("coorResidYn").asText(""),
                node.path("specFcltyInfo").asText(""),
                node.path("corprHsptlInfo").asText(""),
                node.path("trtmntGdsKndInfo").asText(""));
    }
}
