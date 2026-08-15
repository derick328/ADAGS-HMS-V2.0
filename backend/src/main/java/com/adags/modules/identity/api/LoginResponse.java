package com.adags.modules.identity.api;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String accessToken,
        Instant accessTokenExpiresAt,
        boolean mustChangePassword
) {
}
