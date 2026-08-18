package com.adags.modules.encounters;

import com.adags.shared.kernel.EncounterContext;

import java.util.Optional;
import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). Returns shared/kernel vocabulary, never
// com.adags.modules.encounters.domain.Encounter — first caller is the
// laboratory module, which needs an order's patient/facility/department/
// payer type without importing the Encounter entity across the boundary.
public interface EncounterFacade {

    Optional<EncounterContext> findContext(UUID encounterId);

    /**
     * 07-doctor.md §7's ADMITTED disposition: IN_CONSULT -> ADMITTED.
     * Throws InvalidEncounterTransitionException if the encounter's current
     * state does not allow it. The inpatient module calls this rather than
     * importing EncounterState — same reasoning as every other cross-module
     * write in this codebase going through a facade method, not the entity.
     */
    void admit(UUID encounterId, UUID actorUserId);

    /**
     * 17-ward-nurse.md §1's resolution table: ADMITTED -> IN_WARD, called on
     * an admission's first bed assignment only — a transfer's later
     * assignments must not call this again.
     */
    void moveToWard(UUID encounterId, UUID actorUserId);
}
