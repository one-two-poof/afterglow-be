package com.afterglow.web.dto;

import com.afterglow.domain.User;
import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String name,
        String profileImageUrl,
        String role,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProfileImageUrl(),
                user.getRole().name(),
                user.getCreatedAt());
    }
}
