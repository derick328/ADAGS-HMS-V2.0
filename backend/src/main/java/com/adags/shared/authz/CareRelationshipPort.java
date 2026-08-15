package com.adags.shared.authz;

import java.util.UUID;

// Deliberately has no real implementation wired in Phase 0 — Patient and
// Encounter don't exist yet. The `encounters` module implements this in
// Phase 1 (an open encounter attended by, or scoped to, the actor). Until
// then NoCareRelationshipPort always answers false, so a held permission
// with no relationship correctly falls through to BREAK_GLASS_REQUIRED.
public interface CareRelationshipPort {

    boolean hasCareRelationship(UUID actorUserId, UUID patientId);
}
