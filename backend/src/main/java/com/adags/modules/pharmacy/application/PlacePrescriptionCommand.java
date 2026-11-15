package com.adags.modules.pharmacy.application;

import com.adags.modules.pharmacy.domain.PrescriptionFrequency;

import java.util.UUID;

// route/doseAmount/doseUnit/frequency/durationDays are all optional —
// 17-ward-nurse.md §5.1's structured MAR fields, nullable because an
// outpatient prescription with no admission has no reason to carry them.
public record PlacePrescriptionCommand(
        UUID encounterId,
        UUID itemId,
        double quantityPrescribed,
        String dosageInstructions,
        boolean substitutionAllowed,
        UUID prescribedBy,
        String route,
        Double doseAmount,
        String doseUnit,
        PrescriptionFrequency frequency,
        Integer durationDays,
        Number requestedQuantity,
        String requestedUnit
) {
    public PlacePrescriptionCommand(UUID encounterId, UUID itemId, double quantityPrescribed,
            String dosageInstructions, boolean substitutionAllowed, UUID prescribedBy,
            String route, Double doseAmount, String doseUnit,
            PrescriptionFrequency frequency, Integer durationDays) {
        this(encounterId, itemId, quantityPrescribed, dosageInstructions, substitutionAllowed,
                prescribedBy, route, doseAmount, doseUnit, frequency, durationDays, null, null);
    }
}
