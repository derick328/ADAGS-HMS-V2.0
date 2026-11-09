package com.adags.modules.clinical.application;

import com.adags.modules.clinical.infrastructure.JdbcPregnancyAssessmentRepository;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class RecordPregnancyAssessmentService {
    private final JdbcPregnancyAssessmentRepository assessments;
    private final DomainEventPublisher events;

    public RecordPregnancyAssessmentService(JdbcPregnancyAssessmentRepository assessments, DomainEventPublisher events) {
        this.assessments = assessments;
        this.events = events;
    }

    @Transactional
    public void record(UUID encounterId, UUID patientId, String status, UUID actorId) {
        assessments.append(encounterId, status, actorId);
        events.publish("PregnancyAssessmentRecorded", Map.of(
                "encounterId", encounterId.toString(), "patientId", patientId.toString()));
    }
}
