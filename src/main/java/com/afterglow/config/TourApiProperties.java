package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tourapi")
public record TourApiProperties(String serviceKey, String baseUrl, String engBaseUrl, String jpnBaseUrl, String mobileApp) {

    public TourApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://apis.data.go.kr/B551011/KorService2";
        }
        if (engBaseUrl == null || engBaseUrl.isBlank()) {
            engBaseUrl = "https://apis.data.go.kr/B551011/EngService2";
        }
        if (jpnBaseUrl == null || jpnBaseUrl.isBlank()) {
            jpnBaseUrl = "https://apis.data.go.kr/B551011/JpnService2";
        }
        if (mobileApp == null || mobileApp.isBlank()) {
            mobileApp = "afterglow";
        }
    }

    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
