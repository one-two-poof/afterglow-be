package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medicaltourism")
public record MedicalTourismProperties(
        String serviceKey,
        String baseUrl,
        String mobileApp,
        String defaultLang) {

    public MedicalTourismProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://apis.data.go.kr/B551011/MdclTursmService";
        }
        if (mobileApp == null || mobileApp.isBlank()) {
            mobileApp = "afterglow";
        }
        if (defaultLang == null || defaultLang.isBlank()) {
            defaultLang = "ENG";
        }
    }

    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
