package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.MedicationAdministration;
import com.adags.modules.inpatient.domain.MedicationAdministrationStatus;
import com.adags.modules.inpatient.domain.OmissionReason;

import java.time.Instant;
import java.util.UUID;

public record MedicationAdministrationResponse(
        UUID id,
        UUID admissionId,
        UUID prescriptionLineId,
        Instant scheduledAt,
        MedicationAdministrationStatus status,
        Instant administeredAt,
        UUID administeredBy,
        String doseGiven,
        UUID batchId,
        UUID witnessUserId,
        OmissionReason omissionReason,
        String omissionNotes,
        String remarks
) {
    public static MedicationAdministrationResponse from(MedicationAdministration administration) {
        return new MedicationAdministrationResponse(administration.id(), administration.admissionId(),
                administration.prescriptionLineId(), administration.scheduledAt(), administration.status(),
                administration.administeredAt(), administration.administeredBy(), administration.doseGiven(),
                administration.batchId(), administration.witnessUserId(), administration.omissionReason(),
                administration.omissionNotes(), administration.remarks());
    }
}
