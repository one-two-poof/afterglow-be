package com.afterglow.web.dto;

import com.afterglow.service.NotionSyncService;
import java.time.Instant;

public record SyncResultResponse(
        boolean ran,
        int fetched,
        int created,
        int updated,
        int archivedMissing,
        Instant syncedAt,
        String message) {

    public static SyncResultResponse from(NotionSyncService.SyncResult result) {
        return new SyncResultResponse(
                result.ran(),
                result.fetched(),
                result.created(),
                result.updated(),
                result.archivedMissing(),
                result.syncedAt(),
                result.message());
    }
}
