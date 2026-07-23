package com.afterglow.web.dto;

import com.afterglow.domain.ContentItem;
import com.afterglow.util.JsonHelper;
import java.time.Instant;
import java.util.Map;

public record HospitalResponse(
        Long id,
        String notionPageId,
        String name,
        String status,
        Map<String, String> properties,
        Instant notionLastEditedAt,
        Instant syncedAt,
        boolean archived) {

    public static HospitalResponse from(ContentItem item, JsonHelper jsonHelper) {
        return new HospitalResponse(
                item.getId(),
                item.getNotionPageId(),
                item.getTitle(),
                item.getStatus(),
                jsonHelper.fromJson(item.getPropertiesJson()),
                item.getNotionLastEditedAt(),
                item.getSyncedAt(),
                item.isArchived());
    }
}
