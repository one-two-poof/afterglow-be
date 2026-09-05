package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "place-detail.backfill")
public record PlaceDetailBackfillProperties(boolean enabled, String cron) {

    public PlaceDetailBackfillProperties {
        if (cron == null || cron.isBlank()) {
            cron = "0 0 5 * * *"; // 매일 새벽 5시 (병원/숙소/관광명소 동기화 4:00/4:30/4:45 다음, 번역 백필 5:30보다 먼저)
        }
    }
}
