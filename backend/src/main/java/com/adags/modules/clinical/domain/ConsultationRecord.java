package com.adags.modules.clinical.domain;

import java.time.Instant;
import java.util.UUID;

// 07-doctor.md §5.3, §10. chief_complaint is deliberately not a field here
// — triage records the patient's own words (06-triage.md §2.1);
// historyOfPresentingIllness is the clinical expansion, not a restatement.
// Construction goes through OpenConsultationRecordService (a fresh OPEN
// row with every free-text field null) rather than a validating static
// factory the way TriageAssessment uses one — unlike triage's one-shot
// submission, this record is built up over many autosave writes and only
// validated as a whole at finalize time (FinalizeConsultationRecordService).
public record ConsultationRecord(
        UUID id,
        UUID encounterId,
        UUID authorId,
        ConsultationRecordStatus status,
        String historyOfPresentingIllness,
        String pastMedicalHistory,
        String pastSurgicalHistory,
        String familySocialHistory,
        String physicalExamination,
        String clinicalNotes,
        String treatmentPlan,
        boolean retrospectiveCompletion,
        String retrospectiveNote,
        Instant completedAt,
        String abandonedReason,
        Instant abandonedAt,
        UUID abandonedBy,
        UUID amendsId,
        String amendmentReason,
        Instant createdAt,
        Instant updatedAt
) {

    public static ConsultationRecord open(UUID encounterId, UUID authorId) {
        return new ConsultationRecord(null, encounterId, authorId, ConsultationRecordStatus.OPEN,
                null, null, null, null, null, null, null,
                false, null, null, null, null, null, null, null, null, null);
    }
}
