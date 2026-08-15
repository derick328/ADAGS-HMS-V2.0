package com.adags.shared.authz;

import java.util.List;
import java.util.UUID;

// Returns every assignment, including expired and revoked ones — the service,
// not the query, decides what is active (isActiveAt), so that rule is unit-testable.
public interface RoleAssignmentPort {

    List<RoleAssignmentView> findAssignments(UUID userId);
}
