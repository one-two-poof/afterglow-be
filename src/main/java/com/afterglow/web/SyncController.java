package com.afterglow.web;

import com.afterglow.service.NotionSyncService;
import com.afterglow.web.dto.SyncResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
@Tag(name = "Sync", description = "Notion 콘텐츠 DB 동기화 수동 트리거")
public class SyncController {

    private final NotionSyncService notionSyncService;

    public SyncController(NotionSyncService notionSyncService) {
        this.notionSyncService = notionSyncService;
    }

    @Operation(
            summary = "Notion 동기화 수동 트리거",
            description = "Notion 콘텐츠 데이터베이스의 항목들을 가져와 로컬 DB에 반영한다.")
    @PostMapping
    public SyncResultResponse triggerSync() {
        return SyncResultResponse.from(notionSyncService.syncFromNotion());
    }
}
