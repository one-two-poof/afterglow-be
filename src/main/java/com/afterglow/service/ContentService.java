package com.afterglow.service;

import com.afterglow.config.NotionProperties;
import com.afterglow.domain.ContentItem;
import com.afterglow.notion.NotionClient;
import com.afterglow.notion.NotionPageSnapshot;
import com.afterglow.repository.ContentItemRepository;
import com.afterglow.util.JsonHelper;
import com.afterglow.web.dto.ContentItemResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentService {

    private final NotionProperties notionProperties;
    private final NotionClient notionClient;
    private final ContentItemRepository contentItemRepository;
    private final JsonHelper jsonHelper;

    public ContentService(
            NotionProperties notionProperties,
            NotionClient notionClient,
            ContentItemRepository contentItemRepository,
            JsonHelper jsonHelper) {
        this.notionProperties = notionProperties;
        this.notionClient = notionClient;
        this.contentItemRepository = contentItemRepository;
        this.jsonHelper = jsonHelper;
    }

    public List<ContentItemResponse> listFromDatabase(boolean includeArchived) {
        List<ContentItem> items = includeArchived
                ? contentItemRepository.findAllByOrderByNotionLastEditedAtDesc()
                : contentItemRepository.findByArchivedFalseOrderByNotionLastEditedAtDesc();
        return items.stream().map(ContentItemResponse::from).toList();
    }

    public Optional<ContentItemResponse> getFromDatabase(String notionPageId) {
        return contentItemRepository.findByNotionPageId(notionPageId).map(ContentItemResponse::from);
    }

    @Transactional
    public ContentItemResponse getFreshFromNotion(String notionPageId) {
        if (!notionProperties.isConfigured()) {
            throw new NotionClient.NotionApiException(
                    "Notion is not configured. Set NOTION_API_TOKEN and NOTION_DATABASE_ID.");
        }

        NotionPageSnapshot snapshot = notionClient.fetchPage(notionPageId);
        Instant syncedAt = Instant.now();

        String propertiesJson = jsonHelper.toJson(snapshot.properties());

        ContentItem item = contentItemRepository
                .findByNotionPageId(notionPageId)
                .orElseGet(() -> new ContentItem(
                        snapshot.pageId(),
                        snapshot.title(),
                        snapshot.status(),
                        propertiesJson,
                        snapshot.lastEditedAt(),
                        syncedAt,
                        snapshot.archived()));

        item.updateFromNotion(
                snapshot.title(),
                snapshot.status(),
                propertiesJson,
                snapshot.lastEditedAt(),
                syncedAt,
                snapshot.archived());

        return ContentItemResponse.from(contentItemRepository.save(item));
    }
}
