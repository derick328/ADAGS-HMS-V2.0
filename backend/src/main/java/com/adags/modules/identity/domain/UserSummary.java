package com.adags.modules.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record UserSummary(UUID id, String username, String status, boolean mustChangePassword, Instant createdAt) {
}
