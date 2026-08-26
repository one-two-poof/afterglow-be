package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tourapi")
public record TourApiProperties(String serviceKey, String baseUrl, String mobileApp) {

    public TourApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://apis.data.go.kr/B551011/KorService2";
        }
        if (mobileApp == null || mobileApp.isBlank()) {
            mobileApp = "afterglow";
        }
    }

    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
