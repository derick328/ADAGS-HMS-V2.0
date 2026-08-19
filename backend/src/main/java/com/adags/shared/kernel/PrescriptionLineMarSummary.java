package com.adags.shared.kernel;

import java.time.Instant;
import java.util.UUID;

// What PharmacyFacade hands inpatient's MAR view — 17-ward-nurse.md §5.1:
// "drug, dose and route are READ-ONLY, inherited from the prescription."
// Never a com.adags.modules.pharmacy.domain type across the boundary.
// frequency is the raw enum name (or null), same treatment ClinicalFacade
// gives Avpu — inpatient owns its own MedicationFrequency copy.
public record PrescriptionLineMarSummary(
        UUID prescriptionLineId,
        UUID itemId,
        String itemName,
        String route,
        Double doseAmount,
        String doseUnit,
        String frequency,
        Integer durationDays,
        Instant prescribedAt,
        UUID prescribedBy,
        boolean requiresAdministrationWitness
) {
}
