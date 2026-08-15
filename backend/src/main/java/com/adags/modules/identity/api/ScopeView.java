package com.adags.modules.identity.api;

import java.time.Instant;
import java.util.UUID;

public record ScopeView(
        String roleCode,
        String scopeType,
        UUID scopeId,
        Instant validFrom,
        Instant validTo
) {
}
