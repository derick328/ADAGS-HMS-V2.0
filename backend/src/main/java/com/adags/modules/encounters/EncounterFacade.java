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
}
