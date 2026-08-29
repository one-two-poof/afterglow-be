package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "place-translation.backfill")
public record PlaceTranslationBackfillProperties(boolean enabled, String cron) {

    public PlaceTranslationBackfillProperties {
        if (cron == null || cron.isBlank()) {
            cron = "0 30 5 * * *"; // 매일 새벽 5시 30분 (병원/숙소/관광명소 동기화 4:00/4:30/4:45 다음)
        }
    }
}
