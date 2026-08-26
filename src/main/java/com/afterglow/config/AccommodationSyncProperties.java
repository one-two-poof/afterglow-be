package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "accommodation.sync")
public record AccommodationSyncProperties(boolean enabled, String cron) {

    public AccommodationSyncProperties {
        if (cron == null || cron.isBlank()) {
            cron = "0 30 4 * * *"; // 매일 새벽 4시 30분 (병원 동기화 4시 다음)
        }
    }
}
