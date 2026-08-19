package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.domain.PrescriptionFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

// 17-ward-nurse.md §5.1: route/doseAmount/doseUnit/frequency/durationDays
// are optional — only present when the prescriber wants a MAR schedule
// generated (ward patients). An outpatient prescription can leave them null.
public record PlacePrescriptionRequest(
        @NotNull UUID encounterId,
        @NotNull UUID itemId,
        @Positive int quantityPrescribed,
        @NotBlank String dosageInstructions,
        boolean substitutionAllowed,
        String route,
        Double doseAmount,
        String doseUnit,
        PrescriptionFrequency frequency,
        Integer durationDays
) {
}
