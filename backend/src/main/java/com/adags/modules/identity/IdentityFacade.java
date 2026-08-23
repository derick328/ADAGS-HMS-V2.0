package com.adags.modules.identity;

import com.adags.shared.kernel.UserRef;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). No module holds a "who did this" display name of its own —
// every actor reference elsewhere is a bare userId (Encounter.createdBy,
// ShiftHandover.outgoingNurseId, TriageAssessment.assessedBy, ...) — so any
// screen that needs to show one goes through here rather than reaching
// into com.adags.modules.identity.domain directly.
public interface IdentityFacade {

    /** Empty when the id doesn't resolve to a user — callers show a fallback, never assume presence. */
    Optional<String> findUsername(UUID userId);

    /**
     * Active holders of this role code, right now — 06-triage.md §5.1's
     * referral picker needs "who holds DOCTOR/SPECIALIST_DOCTOR" and no
     * other module may query role_assignment directly.
     */
    List<UserRef> findActiveUsersWithRole(String roleCode);
}
