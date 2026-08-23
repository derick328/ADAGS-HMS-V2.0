package com.adags.modules.identity.api;

import com.adags.modules.identity.domain.RoleAssignmentRecord;
import com.adags.shared.authz.ScopeType;

import java.time.Instant;
import java.util.UUID;

public record RoleAssignmentResponse(
        UUID id,
        UUID userId,
        String roleCode,
        ScopeType scopeType,
        UUID scopeId,
        Instant validFrom,
        Instant validTo,
        Instant revokedAt
) {

    public static RoleAssignmentResponse from(RoleAssignmentRecord record) {
        return new RoleAssignmentResponse(record.id(), record.userId(), record.roleCode(), record.scopeType(),
                record.scopeId(), record.validFrom(), record.validTo(), record.revokedAt());
    }
}
