package com.adags.modules.identity.api;

import com.adags.shared.authz.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record GrantRoleAssignmentRequest(
        @NotBlank String roleCode,
        @NotNull ScopeType scopeType,
        UUID scopeId,
        Instant validFrom,
        Instant validTo
) {
}
