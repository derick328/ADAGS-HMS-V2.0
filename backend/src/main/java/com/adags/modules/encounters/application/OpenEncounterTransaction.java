package com.adags.modules.encounters.application;

import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterEvent;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
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
    private final DomainEventPublisher eventPublisher;

    OpenEncounterTransaction(EncounterRepository encounterRepository, DomainEventPublisher eventPublisher) {
        this.encounterRepository = encounterRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    Encounter run(Encounter toInsert, UUID actorUserId) {
        Encounter saved = encounterRepository.insert(toInsert);

        // Same transaction as the insert above — if this write fails, the
        // encounter row rolls back with it (01-architecture.md §2.1 rule 4).
        encounterRepository.insertEvent(new EncounterEvent(
                null, saved.id(), null, EncounterState.AWAITING_TRIAGE, actorUserId, null, null));

        eventPublisher.publish("EncounterOpened", Map.of(
                "encounterId", saved.id().toString(),
                "patientId", saved.patientId().toString(),
                "facilityId", saved.facilityId().toString(),
                "departmentId", saved.departmentId().toString()));

        return saved;
    }
}
