package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordNotOpenException;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import com.adags.modules.clinical.domain.ConsultationRecordStatus;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

// 07-doctor.md §10 / item 4: "Autosave server-side every 20 seconds keyed
// to (encounter_id, author_id)." Deliberately unvalidated beyond
// "still OPEN" — a draft is allowed to be partial or empty; §5.1's required
// fields are only enforced at finalize (FinalizeConsultationRecordService).
@Service
public class AutosaveConsultationRecordService {

    private final ConsultationRecordRepository repository;

    public AutosaveConsultationRecordService(ConsultationRecordRepository repository) {
        this.repository = repository;
    }

    public void autosave(AutosaveConsultationRecordCommand command) {
        ConsultationRecord record = repository.findById(command.consultationRecordId())
                .orElseThrow(() -> new NoSuchElementException("ConsultationRecord not found: " + command.consultationRecordId()));
        if (record.status() != ConsultationRecordStatus.OPEN) {
            throw new ConsultationRecordNotOpenException(record.id());
        }

        repository.autosave(record.id(), command.historyOfPresentingIllness(), command.pastMedicalHistory(),
                command.pastSurgicalHistory(), command.comorbidities(), command.familySocialHistory(),
                command.physicalExamination(), command.clinicalNotes(), command.treatmentPlan());
    }
}
