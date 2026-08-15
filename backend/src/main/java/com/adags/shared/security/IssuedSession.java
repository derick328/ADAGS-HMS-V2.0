package com.adags.shared.security;

import java.time.Instant;
import java.util.UUID;

public record IssuedSession(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UUID userId
) {
}
