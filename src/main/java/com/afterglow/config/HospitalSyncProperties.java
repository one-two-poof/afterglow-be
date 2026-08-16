package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hospital.sync")
public record HospitalSyncProperties(boolean enabled, String cron) {

    public HospitalSyncProperties {
        if (cron == null || cron.isBlank()) {
            cron = "0 0 4 * * *"; // 매일 새벽 4시
        }
    }
}
