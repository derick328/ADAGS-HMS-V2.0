package com.adags.modules.encounters.application;

import com.adags.modules.billing.BillingFacade;
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

// Row 8 of the encounter transition table. 02-billing-architecture.md
// §8.1: a patient in AWAITING_PAYMENT who cannot pay leaves. The
// consultation charge is voided with a reason — billingFacade.
// voidUnpaidConsultationCharge only succeeds while nothing has been paid
// against it, so this whole abandonment aborts (not silently proceeds)
// if the charge was actually settled in the meantime. The
// TriageAssessment itself is never touched here — nursing time was
// spent, and it is a clinical record (root CLAUDE.md "Data": nothing
// clinical is ever hard-deleted); this transition only ever writes the
// encounter row and event.
@Service
public class AbandonUnpaidEncounterService {

    private final EncounterRepository encounterRepository;
    private final BillingFacade billingFacade;
    private final DomainEventPublisher eventPublisher;

    public AbandonUnpaidEncounterService(
            EncounterRepository encounterRepository, BillingFacade billingFacade, DomainEventPublisher eventPublisher
    ) {
        this.encounterRepository = encounterRepository;
        this.billingFacade = billingFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Encounter abandon(UUID encounterId, String reason, UUID actorUserId) {
        if (reason == null || reason.isBlank()) {
            throw new MissingAbandonmentReasonException();
        }

        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));

        EncounterState target = EncounterState.ABANDONED_UNPAID;
        if (!encounter.state().canTransitionTo(target)) {
            throw new InvalidEncounterTransitionException(encounter.state(), target);
        }

        billingFacade.voidUnpaidConsultationCharge(encounterId, reason, actorUserId);

        encounterRepository.updateState(encounterId, target);
        encounterRepository.insertEvent(new EncounterEvent(
                null, encounter.id(), encounter.state(), target, actorUserId, null, reason));

        eventPublisher.publish("EncounterAbandonedUnpaid", Map.of(
                "encounterId", encounter.id().toString(),
                "patientId", encounter.patientId().toString()));

        return new Encounter(encounter.id(), encounter.patientId(), encounter.facilityId(),
                encounter.departmentId(), encounter.appointmentId(), encounter.assignedProviderId(), encounter.type(),
                target, encounter.payerType(), encounter.version() + 1, encounter.createdAt(), encounter.createdBy());
    }
}
