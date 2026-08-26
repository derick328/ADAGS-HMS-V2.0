package com.adags.shared.kernel;

import java.util.UUID;

// The ward an encounter's active admission currently occupies, handed
// across the InpatientFacade boundary without exposing
// com.adags.modules.inpatient.domain.Ward (root CLAUDE.md "Modules") —
// same reasoning as ItemSummary for InventoryFacade. Resolved live from
// the active admission's unreleased BedAssignment, so it is absent for an
// outpatient encounter and for a patient admitted but not yet given a bed.
public record WardSummary(UUID wardId, String name) {
}
