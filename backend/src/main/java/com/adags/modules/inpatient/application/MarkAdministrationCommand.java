package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.MedicationAdministrationStatus;
import com.adags.modules.inpatient.domain.OmissionReason;

import java.util.UUID;

public record MarkAdministrationCommand(
        UUID medicationAdministrationId,
        MedicationAdministrationStatus status,
        UUID actorUserId,
        String doseGiven,
        UUID batchId,
        UUID witnessUserId,
        OmissionReason omissionReason,
        String omissionNotes,
        String remarks
) {
}
