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
    private final Duration refreshTtl;

    public RedisSessionStore(
            StringRedisTemplate redisTemplate,
            Clock clock,
            @Value("${adags.auth.access-token-ttl-minutes}") long accessTtlMinutes,
            @Value("${adags.auth.refresh-token-ttl-days}") long refreshTtlDays
    ) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    @Override
    public IssuedSession issueSession(UUID userId) {
        return storeNewSession(userId);
    }

    @Override
    public Optional<UUID> resolveAccessToken(String accessToken) {
        String value = redisTemplate.opsForValue().get(ACCESS_KEY_PREFIX + accessToken);
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    @Override
    public IssuedSession rotateRefreshToken(String refreshToken) {
        String key = REFRESH_KEY_PREFIX + refreshToken;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new InvalidTokenException();
        }
        redisTemplate.delete(key);
        return storeNewSession(UUID.fromString(value));
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

    private IssuedSession storeNewSession(UUID userId) {
        String accessToken = OpaqueTokenGenerator.generate();
        String refreshToken = OpaqueTokenGenerator.generate();
        Instant now = clock.instant();

        redisTemplate.opsForValue().set(ACCESS_KEY_PREFIX + accessToken, userId.toString(), accessTtl);
        redisTemplate.opsForValue().set(REFRESH_KEY_PREFIX + refreshToken, userId.toString(), refreshTtl);

        return new IssuedSession(accessToken, now.plus(accessTtl), refreshToken, now.plus(refreshTtl), userId);
    }
}
