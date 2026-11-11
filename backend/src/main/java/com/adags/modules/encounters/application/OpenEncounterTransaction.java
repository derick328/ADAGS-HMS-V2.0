package com.adags.modules.encounters.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterEvent;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.scheduling.SchedulingFacade;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

// Split out from OpenEncounterService on purpose: when the insert below
// loses the one-open-encounter-per-patient race, Postgres aborts the whole
// transaction (SQLSTATE 25P02) — no further statement on that connection
// will run until it rolls back. OpenEncounterService's recovery query has
// to happen in a genuinely new transaction, which means this method must be
// invoked through the Spring proxy from a different bean; a private method
// or a self-call would silently skip @Transactional and hit the same
// aborted-transaction error.
@Component
class OpenEncounterTransaction {

    private final EncounterRepository encounterRepository;
    private final SchedulingFacade schedulingFacade;
    private final BillingFacade billingFacade;
    private final DomainEventPublisher eventPublisher;

    OpenEncounterTransaction(EncounterRepository encounterRepository, SchedulingFacade schedulingFacade,
                              BillingFacade billingFacade, DomainEventPublisher eventPublisher) {
        this.encounterRepository = encounterRepository;
        this.schedulingFacade = schedulingFacade;
        this.billingFacade = billingFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    OpenEncounterService.CheckInResult runAfterCompletedVisit(Encounter toInsert, UUID actorUserId) {
        // The lock serializes a new arrival with another check-in or a state change.
        // Only a clinically completed visit in the same department can be
        // finalized by this receptionist action.
        var current = encounterRepository.findOpenByPatientIdForUpdate(toInsert.patientId());
        if (current.isPresent()) {
            Encounter previous = current.get();
            if ((previous.state() != EncounterState.CONSULT_COMPLETE
                    && previous.state() != EncounterState.PENDING_SETTLEMENT)
                    || !toInsert.facilityId().equals(previous.facilityId())
                    || (toInsert.departmentId() != null && !toInsert.departmentId().equals(previous.departmentId()))) {
                return new OpenEncounterService.CheckInResult(previous, true);
            }
            if (previous.state() == EncounterState.CONSULT_COMPLETE) {
                transition(previous.id(), EncounterState.CONSULT_COMPLETE, EncounterState.PENDING_SETTLEMENT, actorUserId);
            }
            transition(previous.id(), EncounterState.PENDING_SETTLEMENT, EncounterState.CLOSED, actorUserId);
            billingFacade.createReceivablesForOutstandingBalance(previous.id());
            eventPublisher.publish("EncounterClosed", Map.of(
                    "encounterId", previous.id().toString(),
                    "patientId", previous.patientId().toString(),
                    "toState", EncounterState.CLOSED.name()));
        }
        return new OpenEncounterService.CheckInResult(run(toInsert, actorUserId), false);
    }

    private void transition(UUID encounterId, EncounterState from, EncounterState to, UUID actorUserId) {
        encounterRepository.updateState(encounterId, to);
        encounterRepository.insertEvent(new EncounterEvent(null, encounterId, from, to, actorUserId, null, null));
    }

    @Transactional
    Encounter run(Encounter toInsert, UUID actorUserId) {
        if (toInsert.appointmentId() != null) {
            schedulingFacade.validateCheckIn(toInsert.appointmentId(), toInsert.patientId(), toInsert.departmentId());
        }
        Encounter saved = encounterRepository.insert(toInsert);

        // Same transaction as the insert above — if this write fails, the
        // encounter row rolls back with it (01-architecture.md §2.1 rule 4).
        encounterRepository.insertEvent(new EncounterEvent(
                null, saved.id(), null, EncounterState.AWAITING_TRIAGE, actorUserId, null, null));

        // 05a-receptionist-decisions.md §3.3: arriving against a booked
        // follow-up marks it ATTENDED and records which encounter it became
        // — same transaction as opening the encounter, not a follow-up call.
        if (saved.appointmentId() != null) {
            schedulingFacade.markAttended(saved.appointmentId(), saved.id());
        }

        var payload = new java.util.HashMap<String, String>();
        payload.put("encounterId", saved.id().toString());
        payload.put("patientId", saved.patientId().toString());
        payload.put("facilityId", saved.facilityId().toString());
        if (saved.departmentId() != null) payload.put("departmentId", saved.departmentId().toString());
        eventPublisher.publish("EncounterOpened", payload);

        return saved;
    }
}
