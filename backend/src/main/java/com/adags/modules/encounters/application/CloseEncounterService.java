package com.adags.modules.encounters.application;

import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterEvent;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

// 05-receptionist.md §5.3 "Dismiss". Closing an encounter never deletes or
// deactivates the patient (⚠8) — this only ever touches the encounter row.
@Service
public class CloseEncounterService {

    private static final Set<EncounterState> ACCEPTED_OUTCOMES = EnumSet.of(
            EncounterState.LEFT_WITHOUT_BEING_SEEN, EncounterState.CANCELLED_IN_ERROR, EncounterState.CLOSED);

    private final EncounterRepository encounterRepository;
    private final DomainEventPublisher eventPublisher;

    public CloseEncounterService(EncounterRepository encounterRepository, DomainEventPublisher eventPublisher) {
        this.encounterRepository = encounterRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Encounter close(CloseEncounterCommand command) {
        if (!ACCEPTED_OUTCOMES.contains(command.outcome())) {
            throw new IllegalArgumentException(
                    "Dismiss only accepts LEFT_WITHOUT_BEING_SEEN, CANCELLED_IN_ERROR, or CLOSED, not "
                            + command.outcome() + ".");
        }
        if (command.outcome() == EncounterState.CANCELLED_IN_ERROR
                && (command.reason() == null || command.reason().isBlank())) {
            throw new MissingCloseReasonException();
        }

        Encounter encounter = encounterRepository.findById(command.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + command.encounterId()));

        if (!encounter.state().canTransitionTo(command.outcome())) {
            throw new InvalidEncounterTransitionException(encounter.state(), command.outcome());
        }

        // 02-billing-architecture.md §8: close(encounter) is two independent
        // checks. Clinical — "no open orders" — blocks; there is no Orders
        // module yet, so it is trivially satisfied today. Wire the real
        // check in here (not as a new gate elsewhere) when orders exist.
        //
        // Financial — "if balance > 0: create receivable" — is deliberately
        // NOT implemented: there is no Invoice/billing module to compute a
        // balance from yet. When it exists, that check is additive and MUST
        // remain unable to block this transition — root CLAUDE.md: "There
        // must be no code path where money prevents a patient leaving."
        encounterRepository.updateState(command.encounterId(), command.outcome());

        // Same transaction as the state change above (01-architecture.md
        // §2.1 rule 4) — if this insert fails, the transition rolls back.
        encounterRepository.insertEvent(new EncounterEvent(
                null, encounter.id(), encounter.state(), command.outcome(),
                command.actorUserId(), null, command.reason()));

        eventPublisher.publish("EncounterClosed", Map.of(
                "encounterId", encounter.id().toString(),
                "patientId", encounter.patientId().toString(),
                "toState", command.outcome().name()));

        return new Encounter(encounter.id(), encounter.patientId(), encounter.facilityId(),
                encounter.departmentId(), encounter.appointmentId(), encounter.assignedProviderId(), encounter.type(),
                command.outcome(), encounter.payerType(), encounter.version() + 1,
                encounter.createdAt(), encounter.createdBy());
    }
}
