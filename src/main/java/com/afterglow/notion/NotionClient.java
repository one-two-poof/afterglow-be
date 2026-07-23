package com.afterglow.notion;

import com.afterglow.config.NotionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class NotionClient {

    private final WebClient notionWebClient;
    private final NotionProperties notionProperties;
    private final ObjectMapper objectMapper;

    public NotionClient(
            WebClient notionWebClient, NotionProperties notionProperties, ObjectMapper objectMapper) {
        this.notionWebClient = notionWebClient;
        this.notionProperties = notionProperties;
        this.objectMapper = objectMapper;
    }

    public List<NotionPageSnapshot> queryDatabasePages() {
        requireConfigured();

        List<NotionPageSnapshot> pages = new ArrayList<>();
        String cursor = null;

        do {
            ObjectNode requestBody = objectMapper.createObjectNode();
            if (cursor != null) {
                requestBody.put("start_cursor", cursor);
            }

            JsonNode body = notionWebClient.post()
                    .uri("/v1/databases/{databaseId}/query", notionProperties.databaseId())
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::mapError)
                    .bodyToMono(JsonNode.class)
                    .block();

            if (body == null) {
                break;
            }

            for (JsonNode result : body.path("results")) {
                pages.add(toSnapshot(result));
            }

            boolean hasMore = body.path("has_more").asBoolean(false);
            cursor = hasMore ? body.path("next_cursor").asText(null) : null;
        } while (cursor != null);

        return pages;
    }

    public NotionPageSnapshot fetchPage(String pageId) {
        requireConfigured();

        JsonNode page = notionWebClient.get()
                .uri("/v1/pages/{pageId}", pageId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(JsonNode.class)
                .block();

        if (page == null) {
            throw new NotionApiException("Notion page response was empty for id=" + pageId);
        }
        return toSnapshot(page);
    }

    private NotionPageSnapshot toSnapshot(JsonNode page) {
        JsonNode properties = page.path("properties");
        Map<String, String> allProperties = NotionPropertyExtractor.extractAllAsMap(properties);
        String title = NotionPropertyExtractor.extractTitle(properties, notionProperties.titleProperty());
        String status = NotionPropertyExtractor.extractSelectName(properties, notionProperties.statusProperty());
        Instant lastEdited = Instant.parse(page.path("last_edited_time").asText());
        boolean archived = page.path("archived").asBoolean(false);

        return new NotionPageSnapshot(
                page.path("id").asText(),
                title,
                status,
                allProperties,
                lastEdited,
                archived);
    }

    private void requireConfigured() {
        if (!notionProperties.isConfigured()) {
            throw new NotionApiException(
                    "Notion is not configured. Set NOTION_API_TOKEN and NOTION_DATABASE_ID.");
        }
    }

    private Mono<? extends Throwable> mapError(
            org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(
                        new NotionApiException("Notion API error " + response.statusCode() + ": " + body)));
    }

    public static class NotionApiException extends RuntimeException {
        public NotionApiException(String message) {
            super(message);
        }

        public NotionApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
