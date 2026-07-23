package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "content_items")
public class ContentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notion_page_id", nullable = false, unique = true, length = 64)
    private String notionPageId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(length = 128)
    private String status;

    @Column(name = "properties_json", columnDefinition = "TEXT")
    private String propertiesJson;

    @Column(name = "notion_last_edited_at", nullable = false)
    private Instant notionLastEditedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(nullable = false)
    private boolean archived;

    protected ContentItem() {
    }

    public ContentItem(
            String notionPageId,
            String title,
            String status,
            String propertiesJson,
            Instant notionLastEditedAt,
            Instant syncedAt,
            boolean archived) {
        this.notionPageId = notionPageId;
        this.title = title;
        this.status = status;
        this.propertiesJson = propertiesJson;
        this.notionLastEditedAt = notionLastEditedAt;
        this.syncedAt = syncedAt;
        this.archived = archived;
    }

    public Long getId() {
        return id;
    }

    public String getNotionPageId() {
        return notionPageId;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getPropertiesJson() {
        return propertiesJson;
    }

    public Instant getNotionLastEditedAt() {
        return notionLastEditedAt;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public boolean isArchived() {
        return archived;
    }

    public void updateFromNotion(
            String title,
            String status,
            String propertiesJson,
            Instant notionLastEditedAt,
            Instant syncedAt,
            boolean archived) {
        this.title = title;
        this.status = status;
        this.propertiesJson = propertiesJson;
        this.notionLastEditedAt = notionLastEditedAt;
        this.syncedAt = syncedAt;
        this.archived = archived;
    }
}
