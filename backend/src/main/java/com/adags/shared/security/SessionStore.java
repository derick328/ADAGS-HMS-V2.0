package com.adags.shared.security;

import java.util.Optional;
import java.util.UUID;

public interface SessionStore {

    IssuedSession issueSession(UUID userId);

    Optional<UUID> resolveAccessToken(String accessToken);

    // Single-use: the presented refresh token is consumed and a new pair issued.
    // Throws InvalidTokenException if it is missing, expired, or already used.
    IssuedSession rotateRefreshToken(String refreshToken);

    void revoke(String accessToken, String refreshToken);
}
