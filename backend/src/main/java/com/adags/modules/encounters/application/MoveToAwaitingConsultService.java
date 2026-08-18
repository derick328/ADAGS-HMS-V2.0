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

// Row 7 of the encounter transition table. 02-billing-architecture.md §2.1:
// consultation entry is the one deliberate payment gate. Refused unless
// the consultation invoice line satisfies BillingGate — checked live, at
// the point of this transition, never cached from order time (§3.1).
// billingFacade.authorizeConsultationEntry calls through to the
// domain-layer gate (root CLAUDE.md "Billing": never a controller-only
// check) and throws (unchecked) on refusal — this class never needs to
// import billing.domain to propagate that.
@Service
public class MoveToAwaitingConsultService {

    private final EncounterRepository encounterRepository;
    private final BillingFacade billingFacade;
    private final DomainEventPublisher eventPublisher;

    public MoveToAwaitingConsultService(
            EncounterRepository encounterRepository, BillingFacade billingFacade, DomainEventPublisher eventPublisher
    ) {
        this.encounterRepository = encounterRepository;
        this.billingFacade = billingFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Encounter moveToAwaitingConsult(UUID encounterId, UUID actorUserId) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));

        EncounterState target = EncounterState.AWAITING_CONSULT;
        if (!encounter.state().canTransitionTo(target)) {
            throw new InvalidEncounterTransitionException(encounter.state(), target);
        }

        billingFacade.authorizeConsultationEntry(encounterId, actorUserId);

        encounterRepository.updateState(encounterId, target);
        encounterRepository.insertEvent(new EncounterEvent(
                null, encounter.id(), encounter.state(), target, actorUserId, null, null));

        eventPublisher.publish("EncounterAwaitingConsult", Map.of(
                "encounterId", encounter.id().toString(),
                "patientId", encounter.patientId().toString()));

        return new Encounter(encounter.id(), encounter.patientId(), encounter.facilityId(),
                encounter.departmentId(), encounter.appointmentId(), encounter.assignedProviderId(), encounter.type(),
                target, encounter.payerType(), encounter.version() + 1, encounter.createdAt(), encounter.createdBy());
    }
}
