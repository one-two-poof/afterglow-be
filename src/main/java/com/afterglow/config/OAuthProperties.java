package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(String frontendRedirectUrl) {

    public OAuthProperties {
        if (frontendRedirectUrl == null || frontendRedirectUrl.isBlank()) {
            frontendRedirectUrl = "afterglow://oauth/callback";
        }
    }
}
