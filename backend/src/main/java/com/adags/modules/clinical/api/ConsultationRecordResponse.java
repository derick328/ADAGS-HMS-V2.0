package com.adags.modules.clinical.api;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordStatus;

import java.time.Instant;
import java.util.UUID;

public record ConsultationRecordResponse(
        UUID id,
        UUID encounterId,
        UUID authorId,
        ConsultationRecordStatus status,
        String historyOfPresentingIllness,
        String pastMedicalHistory,
        String pastSurgicalHistory,
        String comorbidities,
        String familySocialHistory,
        String physicalExamination,
        String clinicalNotes,
        String treatmentPlan,
        boolean retrospectiveCompletion,
        String retrospectiveNote,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ConsultationRecordResponse from(ConsultationRecord record) {
        return new ConsultationRecordResponse(
                record.id(), record.encounterId(), record.authorId(), record.status(),
                record.historyOfPresentingIllness(), record.pastMedicalHistory(), record.pastSurgicalHistory(),
                record.comorbidities(), record.familySocialHistory(), record.physicalExamination(), record.clinicalNotes(),
                record.treatmentPlan(),
                record.retrospectiveCompletion(), record.retrospectiveNote(), record.completedAt(),
                record.createdAt(), record.updatedAt());
    }
}
