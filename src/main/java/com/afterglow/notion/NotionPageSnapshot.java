package com.afterglow.notion;

import java.time.Instant;
import java.util.Map;

public record NotionPageSnapshot(
        String pageId,
        String title,
        String status,
        Map<String, String> properties,
        Instant lastEditedAt,
        boolean archived) {
}
