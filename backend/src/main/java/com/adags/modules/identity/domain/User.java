package com.adags.modules.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String username,
        String passwordHash,
        String status,
        boolean mustChangePassword,
        int failedAttempts,
        Instant lockedUntil
) {

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
