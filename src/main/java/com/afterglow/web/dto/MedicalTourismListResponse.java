package com.afterglow.web.dto;

import java.util.List;

/** 프론트에 내려줄 페이징 포함 목록 응답 */
public record MedicalTourismListResponse(
        int pageNo,
        int numOfRows,
        int totalCount,
        List<MedicalTourismListItem> items) {
}
