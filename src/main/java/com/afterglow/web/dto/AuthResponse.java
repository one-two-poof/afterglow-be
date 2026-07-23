package com.afterglow.web.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresIn) {

    public static AuthResponse of(String token, long expiresInMs) {
        return new AuthResponse(token, "Bearer", expiresInMs / 1000);
    }
}
