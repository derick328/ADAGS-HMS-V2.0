package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.MedicationAdministrationStatus;
import com.adags.modules.inpatient.domain.OmissionReason;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MarkAdministrationRequest(
        @NotNull MedicationAdministrationStatus status,
        String doseGiven,
        UUID batchId,
        UUID witnessUserId,
        OmissionReason omissionReason,
        String omissionNotes,
        String remarks
) {
}
