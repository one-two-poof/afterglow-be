package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "attraction.sync")
public record AttractionSyncProperties(boolean enabled, String cron) {

    public AttractionSyncProperties {
        if (cron == null || cron.isBlank()) {
            cron = "0 45 4 * * *"; // 매일 새벽 4시 45분 (숙소 동기화 4시 30분 다음)
        }
    }
}
