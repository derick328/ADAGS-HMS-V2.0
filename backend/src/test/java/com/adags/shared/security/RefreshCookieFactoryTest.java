package com.adags.shared.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieFactoryTest {
    private final RefreshCookieFactory factory = new RefreshCookieFactory();

    @Test
    void refreshCookieWorksOnHttpAndStaysSecureOnHttps() {
        Instant expiresAt = Instant.now().plusSeconds(3600);

        assertThat(factory.build("token", expiresAt, false).toString())
                .contains("HttpOnly", "SameSite=Strict", "Path=/auth")
                .doesNotContain("Secure");
        assertThat(factory.build("token", expiresAt, true).toString())
                .contains("Secure", "HttpOnly", "SameSite=Strict");
    }
}
