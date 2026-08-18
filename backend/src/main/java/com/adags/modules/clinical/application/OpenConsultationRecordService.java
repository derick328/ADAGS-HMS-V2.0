package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.ConsultationRecord;
import com.adags.modules.clinical.domain.ConsultationRecordRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

// 07-doctor.md §7.1: a receiving doctor on internal referral opens their
// OWN ConsultationRecord alongside the referring doctor's already-finalized
// one — so this resumes the existing OPEN row for (encounterId, authorId)
// if there is one (09-doctor-incomplete-consultations.md §10: "a recovered
// consultation opens with whatever was autosaved"), or starts a fresh one.
@Service
public class OpenConsultationRecordService {

    private final ConsultationRecordRepository repository;

    public OpenConsultationRecordService(ConsultationRecordRepository repository) {
        this.repository = repository;
    }

    public ConsultationRecord open(UUID encounterId, UUID authorId) {
        return repository.findOpenByEncounterAndAuthor(encounterId, authorId)
                .orElseGet(() -> repository.insert(ConsultationRecord.open(encounterId, authorId)));
    }
}
