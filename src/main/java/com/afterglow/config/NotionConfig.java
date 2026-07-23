package com.afterglow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class NotionConfig {

    private static final String NOTION_BASE_URL = "https://api.notion.com";

    @Bean
    WebClient notionWebClient(NotionProperties notionProperties) {
        return WebClient.builder()
                .baseUrl(NOTION_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + notionProperties.apiToken())
                .defaultHeader("Notion-Version", notionProperties.apiVersion())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
