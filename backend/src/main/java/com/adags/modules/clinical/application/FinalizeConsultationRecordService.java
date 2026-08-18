package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordNotOpenException;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.clinical.domain.ConsultationRecordStatus;
import com.adags.modules.clinical.domain.DiagnosisRepository;
import com.adags.modules.clinical.domain.InvalidConsultationRecordException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

// 09-doctor-incomplete-consultations.md §5.1: "Required to finalize" —
// history_of_presenting_illness, physical_examination, provisional_diagnosis
// (at least one primary Diagnosis — §4.1's "exactly one may be primary"),
// treatment_plan. disposition (nextStep) is required too, but that's the
// encounters-module caller's own command field, not something this
// clinical-side check can see — see FinalizeConsultationService.
// final_diagnosis is deliberately NOT checked: 07-doctor.md §4.4, it stays
// nullable when investigations are pending.
@Service
public class FinalizeConsultationRecordService {

    private final ConsultationRecordRepository consultationRecordRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final Clock clock;

    public FinalizeConsultationRecordService(
            ConsultationRecordRepository consultationRecordRepository,
            DiagnosisRepository diagnosisRepository,
            Clock clock
    ) {
        this.consultationRecordRepository = consultationRecordRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.clock = clock;
    }

    @Transactional
    public ConsultationRecord finalize(UUID encounterId, UUID authorId, boolean retrospectiveCompletion, String retrospectiveNote) {
        ConsultationRecord record = consultationRecordRepository.findOpenByEncounterAndAuthor(encounterId, authorId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No OPEN ConsultationRecord for encounter " + encounterId + " and author " + authorId));
        if (record.status() != ConsultationRecordStatus.OPEN) {
            throw new ConsultationRecordNotOpenException(record.id());
        }

        requireNonBlank(record.historyOfPresentingIllness(), "history_of_presenting_illness");
        requireNonBlank(record.physicalExamination(), "physical_examination");
        requireNonBlank(record.treatmentPlan(), "treatment_plan");
        if (!diagnosisRepository.existsPrimaryForEncounter(encounterId)) {
            throw new InvalidConsultationRecordException("provisional_diagnosis is required: no primary diagnosis recorded.");
        }
        // 09-doc §5.2: a consultation completed from the incomplete list —
        // hours or days later — needs its own dated note distinguishing it
        // from a contemporaneous one. The min-20-chars rule is also a DB
        // CHECK (V14 migration); checked here first for a clean message.
        if (retrospectiveCompletion && (retrospectiveNote == null || retrospectiveNote.trim().length() < 20)) {
            throw new InvalidConsultationRecordException(
                    "retrospective_note is required (minimum 20 characters) when retrospective_completion is true.");
        }

        Instant completedAt = Instant.now(clock);
        consultationRecordRepository.finalize(record.id(), completedAt, retrospectiveCompletion, retrospectiveNote);

        return new ConsultationRecord(record.id(), record.encounterId(), record.authorId(),
                ConsultationRecordStatus.FINALIZED, record.historyOfPresentingIllness(), record.pastMedicalHistory(),
                record.pastSurgicalHistory(), record.familySocialHistory(), record.physicalExamination(),
                record.clinicalNotes(), record.treatmentPlan(), retrospectiveCompletion, retrospectiveNote,
                completedAt, record.abandonedReason(), record.abandonedAt(), record.abandonedBy(),
                record.amendsId(), record.amendmentReason(), record.createdAt(), completedAt);
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidConsultationRecordException(field + " is required to finalize.");
        }
    }
}
