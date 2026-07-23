package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notion")
public record NotionProperties(
        String apiToken,
        String databaseId,
        String apiVersion,
        String titleProperty,
        String statusProperty
) {
    public NotionProperties {
        if (apiVersion == null || apiVersion.isBlank()) {
            apiVersion = "2022-06-28";
        }
    }

    public boolean isConfigured() {
        return apiToken != null && !apiToken.isBlank()
                && databaseId != null && !databaseId.isBlank();
    }
}
