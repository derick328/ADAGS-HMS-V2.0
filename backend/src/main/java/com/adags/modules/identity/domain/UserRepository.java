package com.adags.modules.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    Optional<User> findById(UUID id);

    void recordLoginSuccess(UUID userId, Instant loginAt);

    void recordLoginFailure(UUID userId, int failedAttempts, Instant lockedUntil);
}
