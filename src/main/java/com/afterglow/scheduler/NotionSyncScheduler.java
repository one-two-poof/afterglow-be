package com.afterglow.scheduler;

import com.afterglow.config.SyncProperties;
import com.afterglow.service.NotionSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotionSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotionSyncScheduler.class);

    private final SyncProperties syncProperties;
    private final NotionSyncService notionSyncService;

    public NotionSyncScheduler(SyncProperties syncProperties, NotionSyncService notionSyncService) {
        this.syncProperties = syncProperties;
        this.notionSyncService = notionSyncService;
    }

    @Scheduled(cron = "${sync.cron}")
    public void syncNotionDatabase() {
        if (!syncProperties.enabled()) {
            return;
        }
        try {
            notionSyncService.syncFromNotion();
        } catch (Exception ex) {
            log.error("Scheduled Notion sync failed", ex);
        }
    }
}
