package com.adags.shared.kernel;

import java.util.UUID;

// What InventoryFacade hands another module (pharmacy) about a catalogue
// item without exposing com.adags.modules.inventory.domain.Item across the
// boundary — same reasoning as PatientDemographics for PatientFacade.
public record ItemSummary(
        UUID itemId, String code, String name, String unit, boolean dispensable, boolean active,
        boolean requiresAdministrationWitness
) {
}
