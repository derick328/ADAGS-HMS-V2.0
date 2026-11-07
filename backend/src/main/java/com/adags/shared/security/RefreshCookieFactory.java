package com.adags.shared.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RefreshCookieFactory {

    static final String COOKIE_NAME = "adags_refresh";

    // Browsers reject Secure cookies on ordinary HTTP hosts. Keep it on for
    // HTTPS (including requests forwarded through an HTTPS reverse proxy).
    public ResponseCookie build(String refreshToken, Instant expiresAt, boolean secure) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        return ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/auth")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }

    public ResponseCookie expired(boolean secure) {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/auth")
                .maxAge(0)
                .build();
    }
}
