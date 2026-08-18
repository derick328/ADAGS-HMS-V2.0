package com.adags.modules.encounters.application;

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

// Transition row "IN_CONSULT -> AWAITING_RESULTS" (07-doctor.md §8.1/§8.2).
// Deliberately not tied to order placement — the Orders module that would
// normally trigger this doesn't exist yet (root CLAUDE.md build order).
// This is the transition itself, ready for that caller; today it can be
// invoked directly by a doctor indicating they're waiting on results.
// Leaves the ConsultationRecord untouched — it stays OPEN, and
// 09-doctor-incomplete-consultations.md §2's CRITICAL rule is exactly that
// an OPEN record on an AWAITING_RESULTS encounter must not read as
// incomplete (ConsultationRecordRepository.findIncomplete excludes it).
@Service
public class MarkAwaitingResultsService {

    private final EncounterRepository encounterRepository;
    private final DomainEventPublisher eventPublisher;

    public MarkAwaitingResultsService(EncounterRepository encounterRepository, DomainEventPublisher eventPublisher) {
        this.encounterRepository = encounterRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void markAwaitingResults(UUID encounterId, UUID actorUserId) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        if (!encounter.state().canTransitionTo(EncounterState.AWAITING_RESULTS)) {
            throw new InvalidEncounterTransitionException(encounter.state(), EncounterState.AWAITING_RESULTS);
        }

        encounterRepository.updateState(encounterId, EncounterState.AWAITING_RESULTS);
        encounterRepository.insertEvent(new EncounterEvent(
                null, encounterId, encounter.state(), EncounterState.AWAITING_RESULTS, actorUserId, null, null));

        eventPublisher.publish("EncounterAwaitingResults", Map.of(
                "encounterId", encounterId.toString(),
                "patientId", encounter.patientId().toString()));
    }
}
