package com.adags.shared.kernel;

import java.util.UUID;

// Shared/kernel vocabulary (root CLAUDE.md "Modules") so EncounterFacade can
// hand another module the facts an order needs — patient, facility,
// department (for permission scoping) and payer type (for pricing) —
// without exposing com.adags.modules.encounters.domain.Encounter across the
// boundary. Same reasoning as PatientDemographics for PatientFacade.
public record EncounterContext(UUID patientId, UUID facilityId, UUID departmentId, PayerType payerType) {
}
