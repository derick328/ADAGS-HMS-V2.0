package com.adags.modules.identity.application;

import com.adags.shared.authz.ScopeType;

import java.time.Instant;
import java.util.UUID;

public record GrantRoleAssignmentCommand(
        UUID userId,
        String roleCode,
        ScopeType scopeType,
        UUID scopeId,
        Instant validFrom,
        Instant validTo,
        UUID grantedBy
) {
}
