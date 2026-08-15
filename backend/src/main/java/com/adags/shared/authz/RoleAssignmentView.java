package com.adags.shared.authz;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RoleAssignmentView(
        UUID assignmentId,
        String roleCode,
        ScopeRef scope,
        Instant validFrom,
        Instant validTo,
        Instant revokedAt,
        Set<String> permissionCodes
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
