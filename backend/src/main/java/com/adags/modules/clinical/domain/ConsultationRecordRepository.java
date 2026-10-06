package com.adags.modules.clinical.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRecordRepository {

    ConsultationRecord insert(ConsultationRecord record);

    Optional<ConsultationRecord> findById(UUID id);

    /** The autosave key (encounter_id, author_id) — at most one OPEN row per pair. */
    Optional<ConsultationRecord> findOpenByEncounterAndAuthor(UUID encounterId, UUID authorId);

    /** 08-doctor-navigation.md §3.3: every author's record for this encounter, newest first — "other clinicians' notes visible." */
    List<ConsultationRecord> findByEncounterId(UUID encounterId);

    /** Overwrites the free-text fields in place — only valid while status is OPEN. */
    void autosave(UUID id, String historyOfPresentingIllness, String pastMedicalHistory, String pastSurgicalHistory,
                   String comorbidities, String familySocialHistory, String physicalExamination, String clinicalNotes,
                   String treatmentPlan);

    void finalize(UUID id, Instant completedAt, boolean retrospectiveCompletion, String retrospectiveNote);

    /**
     * 09-doctor-incomplete-consultations.md §2: status = OPEN, this author,
     * created before {@code threshold}, encounter not terminal AND not
     * AWAITING_RESULTS (a doctor waiting on labs is not neglecting
     * documentation — excluding it is what keeps this list clearable).
     */
    List<ConsultationRecord> findIncomplete(UUID authorId, Instant threshold);
}
