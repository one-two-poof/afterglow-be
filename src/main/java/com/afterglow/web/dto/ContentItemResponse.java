package com.afterglow.web.dto;

import com.afterglow.domain.ContentItem;
import java.time.Instant;

public record ContentItemResponse(
        Long id,
        String notionPageId,
        String title,
        String status,
        Instant notionLastEditedAt,
        Instant syncedAt,
        boolean archived) {

    public static ContentItemResponse from(ContentItem item) {
        return new ContentItemResponse(
                item.getId(),
                item.getNotionPageId(),
                item.getTitle(),
                item.getStatus(),
                item.getNotionLastEditedAt(),
                item.getSyncedAt(),
                item.isArchived());
    }
}
