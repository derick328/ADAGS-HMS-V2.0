package com.adags.modules.encounters.application;

import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterEvent;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 07-doctor.md §2's entry guard, transition row "AWAITING_CONSULT ->
// IN_CONSULT". The billing-bypass half of canOpenConsultation (encounter.
// state == AWAITING_CONSULT OR active_payment_bypass_exists(encounter)) is
// already satisfied by the state machine itself — the only way to reach
// AWAITING_CONSULT at all is either the normal payment gate or an
// IMMEDIATE bypass (SubmitTriageAssessmentService), so checking
// canTransitionTo(IN_CONSULT) here is sufficient, not a narrower guard.
@Service
public class OpenConsultationService {

    private final EncounterRepository encounterRepository;
    private final ClinicalFacade clinicalFacade;
    private final DomainEventPublisher eventPublisher;

    public OpenConsultationService(
            EncounterRepository encounterRepository, ClinicalFacade clinicalFacade, DomainEventPublisher eventPublisher
    ) {
        this.encounterRepository = encounterRepository;
        this.clinicalFacade = clinicalFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UUID open(UUID encounterId, UUID doctorId) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        if (!encounter.state().canTransitionTo(EncounterState.IN_CONSULT)) {
            throw new InvalidEncounterTransitionException(encounter.state(), EncounterState.IN_CONSULT);
        }

        UUID consultationRecordId = clinicalFacade.openConsultationRecord(encounterId, doctorId);

        encounterRepository.updateState(encounterId, EncounterState.IN_CONSULT);
        encounterRepository.insertEvent(new EncounterEvent(
                null, encounterId, encounter.state(), EncounterState.IN_CONSULT, doctorId, null, null));

        eventPublisher.publish("ConsultationOpened", Map.of(
                "encounterId", encounterId.toString(),
                "patientId", encounter.patientId().toString(),
                "consultationRecordId", consultationRecordId.toString(),
                "doctorId", doctorId.toString()));

        return consultationRecordId;
    }
}
