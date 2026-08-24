package com.adags.modules.clinical.api;

import com.adags.modules.clinical.domain.AllergyConfirmationStatus;
import com.adags.modules.clinical.domain.AllergyRecord;
import com.adags.modules.clinical.domain.AllergySource;

import java.time.Instant;
import java.util.UUID;

public record AllergyRecordResponse(
        UUID id,
        UUID patientId,
        UUID encounterId,
        String description,
        String reaction,
        String severity,
        AllergySource source,
        UUID recordedBy,
        String recordedByName,
        Instant recordedAt,
        AllergyConfirmationStatus confirmationStatus,
        UUID confirmedBy,
        Instant confirmedAt,
        String confirmationNote,
        UUID supersedesId
) {
    public static AllergyRecordResponse from(AllergyRecord record, String recordedByName) {
        return new AllergyRecordResponse(
                record.id(), record.patientId(), record.encounterId(), record.description(), record.reaction(),
                record.severity(), record.source(), record.recordedBy(), recordedByName, record.recordedAt(),
                record.confirmationStatus(), record.confirmedBy(), record.confirmedAt(), record.confirmationNote(),
                record.supersedesId());
    }
}
