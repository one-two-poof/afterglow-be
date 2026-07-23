package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expirationMs) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            secret = "afterglow-default-secret-change-in-production-must-be-long-enough";
        }
        if (expirationMs <= 0) {
            expirationMs = 86400000L; // 24h
        }
    }
}
