package com.adags.modules.identity.api;

import java.time.Instant;
import java.util.UUID;

public record TokenResponse(
        UUID userId,
        String accessToken,
        Instant accessTokenExpiresAt
) {
}
