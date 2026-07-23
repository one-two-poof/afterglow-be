package com.afterglow.web;

import com.afterglow.service.NotionSyncService;
import com.afterglow.web.dto.SyncResultResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final NotionSyncService notionSyncService;

    public SyncController(NotionSyncService notionSyncService) {
        this.notionSyncService = notionSyncService;
    }

    @PostMapping
    public SyncResultResponse triggerSync() {
        return SyncResultResponse.from(notionSyncService.syncFromNotion());
    }
}
