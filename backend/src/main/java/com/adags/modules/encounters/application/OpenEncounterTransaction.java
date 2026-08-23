package com.adags.modules.encounters.application;

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
    private final DomainEventPublisher eventPublisher;

    OpenEncounterTransaction(EncounterRepository encounterRepository, SchedulingFacade schedulingFacade,
                              DomainEventPublisher eventPublisher) {
        this.encounterRepository = encounterRepository;
        this.schedulingFacade = schedulingFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    Encounter run(Encounter toInsert, UUID actorUserId) {
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

        eventPublisher.publish("EncounterOpened", Map.of(
                "encounterId", saved.id().toString(),
                "patientId", saved.patientId().toString(),
                "facilityId", saved.facilityId().toString(),
                "departmentId", saved.departmentId().toString()));

        return saved;
    }
}
