package com.adags.modules.identity.domain;

import com.adags.shared.authz.ScopeType;

import java.time.Instant;
import java.util.UUID;

public record RoleAssignmentRecord(
        UUID id,
        UUID userId,
        String roleCode,
        ScopeType scopeType,
        UUID scopeId,
        Instant validFrom,
        Instant validTo,
        Instant revokedAt,
        UUID grantedBy
) {

    public boolean isActiveAt(Instant asOf) {
        if (revokedAt != null) {
            return false;
        }
        if (asOf.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || asOf.isBefore(validTo);
    }
}
