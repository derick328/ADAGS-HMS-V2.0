package com.adags.modules.identity.domain;

import com.adags.shared.authz.ScopeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleAssignmentRepository {

    RoleAssignmentRecord grant(UUID userId, UUID roleId, ScopeType scopeType, UUID scopeId,
                               Instant validFrom, Instant validTo, UUID grantedBy);

    List<RoleAssignmentRecord> findByUserId(UUID userId);

    /** Currently-active holders of this role code, across every user and scope — IdentityFacade.findActiveUsersWithRole's backing query. */
    List<RoleAssignmentRecord> findActiveByRoleCode(String roleCode, Instant asOf);

    Optional<RoleAssignmentRecord> findById(UUID id);

    /** Sets revoked_at — an immediate, hard end, distinct from a scheduled valid_to expiry. */
    void revoke(UUID id, Instant revokedAt);
}
