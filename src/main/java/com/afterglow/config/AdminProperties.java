package com.afterglow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(String email, String password) {

    public AdminProperties {
        if (email == null || email.isBlank()) {
            email = "dunaduneo@gmail.com";
        }
        if (password != null && password.isBlank()) {
            password = null;
        }
    }
}
