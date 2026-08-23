package com.adags.modules.identity.api;

import com.adags.modules.identity.domain.User;
import com.adags.modules.identity.domain.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, String status, boolean mustChangePassword, Instant createdAt) {

    public static UserResponse from(UserSummary summary) {
        return new UserResponse(summary.id(), summary.username(), summary.status(), summary.mustChangePassword(), summary.createdAt());
    }

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.username(), user.status(), user.mustChangePassword(), null);
    }
}
