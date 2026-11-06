package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.DiagnosisRepository;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class VoidDiagnosisService {

    private final DiagnosisRepository diagnosisRepository;
    private final DomainEventPublisher events;

    public VoidDiagnosisService(DiagnosisRepository diagnosisRepository, DomainEventPublisher events) {
        this.diagnosisRepository = diagnosisRepository;
        this.events = events;
    }

    @Transactional
    public void voidDiagnosis(UUID consultationRecordId, UUID diagnosisId, UUID actorId) {
        if (!diagnosisRepository.voidOnOpenRecord(diagnosisId, consultationRecordId, actorId)) {
            throw new NoSuchElementException("Active diagnosis not found on this open consultation: " + diagnosisId);
        }
        events.publish("DiagnosisVoided", Map.of(
                "diagnosisId", diagnosisId,
                "consultationRecordId", consultationRecordId,
                "voidedBy", actorId));
    }
}
