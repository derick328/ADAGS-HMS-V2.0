package com.adags.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedisSessionStoreTest {

    @Test
    void refreshCannotExtendSessionBeyondFifteenMinutes() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Map<String, String> entries = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> entries.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            entries.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(invocation -> entries.remove(invocation.getArgument(0)) != null);

        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        UUID userId = UUID.randomUUID();
        IssuedSession login = storeAt(redis, started).issueSession(userId);
        assertThat(login.refreshTokenExpiresAt()).isEqualTo(started.plus(Duration.ofMinutes(15)));
        assertThat(login.accessTokenExpiresAt()).isEqualTo(started.plus(Duration.ofMinutes(15)));

        IssuedSession refreshed = storeAt(redis, started.plus(Duration.ofMinutes(12)))
                .rotateRefreshToken(login.refreshToken());
        assertThat(refreshed.refreshTokenExpiresAt()).isEqualTo(login.refreshTokenExpiresAt());
        assertThat(refreshed.accessTokenExpiresAt()).isEqualTo(login.refreshTokenExpiresAt());
        assertThat(storeAt(redis, started.plus(Duration.ofMinutes(14)))
                .resolveAccessToken(refreshed.accessToken())).contains(userId);

        RedisSessionStore expiredStore = storeAt(redis, started.plus(Duration.ofMinutes(15)));
        assertThat(expiredStore.resolveAccessToken(refreshed.accessToken())).isEmpty();
        assertThatThrownBy(() -> expiredStore.rotateRefreshToken(refreshed.refreshToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void oldSevenDayRefreshTokensAreRejected() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(UUID.randomUUID().toString());

        RedisSessionStore store = storeAt(redis, Instant.parse("2026-01-01T00:00:00Z"));
        assertThatThrownBy(() -> store.rotateRefreshToken("old-token"))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(store.resolveAccessToken("old-token")).isEmpty();
    }

    private RedisSessionStore storeAt(StringRedisTemplate redis, Instant now) {
        return new RedisSessionStore(redis, Clock.fixed(now, ZoneOffset.UTC), 15, 15);
    }
}
