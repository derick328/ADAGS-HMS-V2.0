package com.adags.shared.kernel;

import java.util.UUID;

// What InventoryFacade hands another module (pharmacy) about a catalogue
// item without exposing com.adags.modules.inventory.domain.Item across the
// boundary — same reasoning as PatientDemographics for PatientFacade.
//
// classification / form are the catalogue descriptors from 12-pharmacist.md
// §9.1/§12 (e.g. "Antibiotic" / "Tablet"). Nullable — an item created
// before the field was captured, or a UNCLASSIFIED item, carries null.
public record ItemSummary(
        UUID itemId, String code, String name, String unit, String classification, String form,
        boolean dispensable, boolean active, boolean requiresAdministrationWitness
) {
}
