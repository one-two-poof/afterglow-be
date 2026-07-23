package com.afterglow.service;

import com.afterglow.config.NotionProperties;
import com.afterglow.domain.ContentItem;
import com.afterglow.notion.NotionClient;
import com.afterglow.notion.NotionPageSnapshot;
import com.afterglow.repository.ContentItemRepository;
import com.afterglow.util.JsonHelper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotionSyncService {

    private static final Logger log = LoggerFactory.getLogger(NotionSyncService.class);

    private final NotionProperties notionProperties;
    private final NotionClient notionClient;
    private final ContentItemRepository contentItemRepository;
    private final JsonHelper jsonHelper;

    public NotionSyncService(
            NotionProperties notionProperties,
            NotionClient notionClient,
            ContentItemRepository contentItemRepository,
            JsonHelper jsonHelper) {
        this.notionProperties = notionProperties;
        this.notionClient = notionClient;
        this.contentItemRepository = contentItemRepository;
        this.jsonHelper = jsonHelper;
    }

    @Transactional
    public SyncResult syncFromNotion() {
        if (!notionProperties.isConfigured()) {
            log.warn("Skipping Notion sync: NOTION_API_TOKEN or NOTION_DATABASE_ID is not set");
            return SyncResult.skipped("Notion is not configured");
        }

        Instant syncedAt = Instant.now();
        var snapshots = notionClient.queryDatabasePages();
        Set<String> seenPageIds = new HashSet<>();

        int created = 0;
        int updated = 0;

        for (NotionPageSnapshot snapshot : snapshots) {
            seenPageIds.add(snapshot.pageId());
            var existing = contentItemRepository.findByNotionPageId(snapshot.pageId());

            String propertiesJson = jsonHelper.toJson(snapshot.properties());

            if (existing.isEmpty()) {
                contentItemRepository.save(new ContentItem(
                        snapshot.pageId(),
                        snapshot.title(),
                        snapshot.status(),
                        propertiesJson,
                        snapshot.lastEditedAt(),
                        syncedAt,
                        snapshot.archived()));
                created++;
                continue;
            }

            ContentItem item = existing.get();
            if (needsUpdate(item, snapshot, propertiesJson)) {
                item.updateFromNotion(
                        snapshot.title(),
                        snapshot.status(),
                        propertiesJson,
                        snapshot.lastEditedAt(),
                        syncedAt,
                        snapshot.archived());
                updated++;
            } else {
                item.updateFromNotion(
                        item.getTitle(),
                        item.getStatus(),
                        propertiesJson,
                        item.getNotionLastEditedAt(),
                        syncedAt,
                        snapshot.archived());
            }
        }

        int archived = markMissingAsArchived(seenPageIds, syncedAt);

        log.info(
                "Notion sync finished: fetched={}, created={}, updated={}, archivedMissing={}",
                snapshots.size(),
                created,
                updated,
                archived);

        return new SyncResult(true, snapshots.size(), created, updated, archived, syncedAt, null);
    }

    private int markMissingAsArchived(Set<String> seenPageIds, Instant syncedAt) {
        int archived = 0;
        for (ContentItem item : contentItemRepository.findAll()) {
            if (!seenPageIds.contains(item.getNotionPageId()) && !item.isArchived()) {
                item.updateFromNotion(
                        item.getTitle(),
                        item.getStatus(),
                        item.getPropertiesJson(),
                        item.getNotionLastEditedAt(),
                        syncedAt,
                        true);
                archived++;
            }
        }
        return archived;
    }

    private boolean needsUpdate(ContentItem item, NotionPageSnapshot snapshot, String propertiesJson) {
        return item.getNotionLastEditedAt().isBefore(snapshot.lastEditedAt())
                || !item.getTitle().equals(snapshot.title())
                || !java.util.Objects.equals(item.getStatus(), snapshot.status())
                || !java.util.Objects.equals(item.getPropertiesJson(), propertiesJson)
                || item.isArchived() != snapshot.archived();
    }

    public record SyncResult(
            boolean ran,
            int fetched,
            int created,
            int updated,
            int archivedMissing,
            Instant syncedAt,
            String message) {

        public static SyncResult skipped(String message) {
            return new SyncResult(false, 0, 0, 0, 0, null, message);
        }
    }
}
