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

// 07-doctor.md §10 + §7's disposition table, transition row "IN_CONSULT ->
// CONSULT_COMPLETE | REFERRED_OUT | DECEASED". "Finalizing the record is
// NOT closing the encounter" — CONSULT_COMPLETE lands the encounter in
// CONSULT_COMPLETE and stops there; the further CONSULT_COMPLETE ->
// PENDING_SETTLEMENT -> CLOSED chain is a later item (billing settlement),
// not this one.
//
// Order matters: the clinical validation/finalize call happens BEFORE the
// encounter transition, same reasoning as the triage submission's billing
// step — if the note fails to validate, the encounter must not have moved.
@Service
public class FinalizeConsultationService {

    private final EncounterRepository encounterRepository;
    private final ClinicalFacade clinicalFacade;
    private final DomainEventPublisher eventPublisher;

    public FinalizeConsultationService(
            EncounterRepository encounterRepository, ClinicalFacade clinicalFacade, DomainEventPublisher eventPublisher
    ) {
        this.encounterRepository = encounterRepository;
        this.clinicalFacade = clinicalFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Encounter finalize(FinalizeConsultationCommand command) {
        Encounter encounter = encounterRepository.findById(command.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + command.encounterId()));

        EncounterState targetState = EncounterState.valueOf(command.disposition().name());
        if (!encounter.state().canTransitionTo(targetState)) {
            throw new InvalidEncounterTransitionException(encounter.state(), targetState);
        }

        // Throws (InvalidConsultationRecordException / NoSuchElementException)
        // without touching the encounter if the note isn't ready to finalize.
        clinicalFacade.finalizeConsultationRecord(
                command.encounterId(), command.doctorId(), command.retrospectiveCompletion(), command.retrospectiveNote());

        encounterRepository.updateState(command.encounterId(), targetState);
        encounterRepository.insertEvent(new EncounterEvent(
                null, command.encounterId(), encounter.state(), targetState, command.doctorId(), null, null));

        eventPublisher.publish("ConsultationFinalized", Map.of(
                "encounterId", command.encounterId().toString(),
                "patientId", encounter.patientId().toString(),
                "disposition", command.disposition().name()));

        return new Encounter(encounter.id(), encounter.patientId(), encounter.facilityId(), encounter.departmentId(),
                encounter.appointmentId(), encounter.assignedProviderId(), encounter.type(), targetState,
                encounter.payerType(), encounter.version() + 1, encounter.createdAt(), encounter.createdBy());
    }
}
