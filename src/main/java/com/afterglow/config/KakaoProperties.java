package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(String restApiKey, String baseUrl) {

    public KakaoProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://dapi.kakao.com";
        }
    }

    public boolean isConfigured() {
        return restApiKey != null && !restApiKey.isBlank();
    }
}
