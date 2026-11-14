package com.adags.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisSessionStore implements SessionStore {

    private static final String ACCESS_KEY_PREFIX = "session:access:";
    private static final String REFRESH_KEY_PREFIX = "session:refresh:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration sessionTtl;

    public RedisSessionStore(
            StringRedisTemplate redisTemplate,
            Clock clock,
            @Value("${adags.auth.access-token-ttl-minutes}") long accessTtlMinutes,
            @Value("${adags.auth.session-ttl-minutes}") long sessionTtlMinutes
    ) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
        this.sessionTtl = Duration.ofMinutes(sessionTtlMinutes);
    }

    @Override
    public IssuedSession issueSession(UUID userId) {
        return storeNewSession(userId, clock.instant().plus(sessionTtl));
    }

    @Override
    public Optional<UUID> resolveAccessToken(String accessToken) {
        String value = redisTemplate.opsForValue().get(ACCESS_KEY_PREFIX + accessToken);
        return parseSession(value)
                .filter(session -> clock.instant().isBefore(session.expiresAt()))
                .map(StoredSession::userId);
    }

    @Override
    public IssuedSession rotateRefreshToken(String refreshToken) {
        String key = REFRESH_KEY_PREFIX + refreshToken;
        String value = redisTemplate.opsForValue().get(key);
        Optional<StoredSession> session = parseSession(value)
                .filter(stored -> clock.instant().isBefore(stored.expiresAt()));
        if (session.isEmpty()) {
            throw new InvalidTokenException();
        }
        redisTemplate.delete(key);
        return storeNewSession(session.get().userId(), session.get().expiresAt());
    }

    @Override
    public void revoke(String accessToken, String refreshToken) {
        if (accessToken != null) {
            redisTemplate.delete(ACCESS_KEY_PREFIX + accessToken);
        }
        if (refreshToken != null) {
            redisTemplate.delete(REFRESH_KEY_PREFIX + refreshToken);
        }
    }

    private IssuedSession storeNewSession(UUID userId, Instant sessionExpiresAt) {
        String accessToken = OpaqueTokenGenerator.generate();
        String refreshToken = OpaqueTokenGenerator.generate();
        Instant now = clock.instant();
        Duration remaining = Duration.between(now, sessionExpiresAt);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new InvalidTokenException();
        }
        Duration accessLifetime = remaining.compareTo(accessTtl) < 0 ? remaining : accessTtl;
        String storedValue = userId + "|" + sessionExpiresAt.toEpochMilli();

        redisTemplate.opsForValue().set(ACCESS_KEY_PREFIX + accessToken, storedValue, accessLifetime);
        redisTemplate.opsForValue().set(REFRESH_KEY_PREFIX + refreshToken, storedValue, remaining);

        return new IssuedSession(accessToken, now.plus(accessLifetime), refreshToken, sessionExpiresAt, userId);
    }

    private Optional<StoredSession> parseSession(String value) {
        if (value == null) return Optional.empty();
        String[] parts = value.split("\\|", -1);
        if (parts.length != 2) return Optional.empty();
        try {
            return Optional.of(new StoredSession(UUID.fromString(parts[0]), Instant.ofEpochMilli(Long.parseLong(parts[1]))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private record StoredSession(UUID userId, Instant expiresAt) {}
}
