package com.adags.modules.encounters.application;

import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.modules.encounters.domain.EncounterType;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.authz.ScopeHierarchyPort;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.PayerType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

// 05-receptionist.md §5.2 "Send to triage": POST /encounters opens the
// encounter with state = AWAITING_TRIAGE. Registration does not open an
// encounter — arrival is a separate action, this is that action.
//
// Deliberately NOT @Transactional at this level: when the insert (delegated
// to OpenEncounterTransaction) loses the race against a concurrent check-in
// for the same patient, Postgres aborts that transaction outright — the
// recovery query below must run in a fresh one, not the poisoned one.
@Service
public class OpenEncounterService {

    private final EncounterRepository encounterRepository;
    private final PatientFacade patientFacade;
    private final ScopeHierarchyPort scopeHierarchyPort;
    private final OpenEncounterTransaction openEncounterTransaction;
    private final Clock clock;

    public OpenEncounterService(
            EncounterRepository encounterRepository,
            PatientFacade patientFacade,
            ScopeHierarchyPort scopeHierarchyPort,
            OpenEncounterTransaction openEncounterTransaction,
            Clock clock
    ) {
        this.encounterRepository = encounterRepository;
        this.patientFacade = patientFacade;
        this.scopeHierarchyPort = scopeHierarchyPort;
        this.openEncounterTransaction = openEncounterTransaction;
        this.clock = clock;
    }

    public record CheckInResult(Encounter encounter, boolean existingEncounter) {}
    public Encounter checkIn(OpenEncounterCommand command) { return checkInResult(command).encounter(); }
    public CheckInResult checkInResult(OpenEncounterCommand command) {
        if (!patientFacade.exists(command.patientId())) {
            throw new NoSuchElementException("Patient not found: " + command.patientId());
        }

        // Guard — one open encounter per patient (⚠7). This pre-check saves
        // the common case a round trip; the partial unique index below is
        // the real guard, not this read.
        Optional<Encounter> existing = encounterRepository.findOpenByPatientId(command.patientId());
        if (existing.isPresent()) {
            Encounter previous = existing.get();
            boolean completed = previous.state() == EncounterState.CONSULT_COMPLETE
                    || previous.state() == EncounterState.PENDING_SETTLEMENT;
            if (!completed
                    || (command.facilityId() != null && !command.facilityId().equals(previous.facilityId()))
                    || (command.departmentId() != null && !command.departmentId().equals(previous.departmentId()))) {
                return new CheckInResult(previous, true);
            }
        }

        UUID facilityId = command.departmentId() == null ? command.facilityId() : facilityIdFor(command.departmentId());
        if (facilityId == null || (command.facilityId() != null && !facilityId.equals(command.facilityId()))) {
            throw new IllegalArgumentException("A valid encounter facility is required.");
        }
        PayerType payerType = patientFacade.resolvePayerType(command.patientId(), LocalDate.now(clock));

        Encounter toInsert = new Encounter(null, command.patientId(), facilityId, command.departmentId(),
                command.appointmentId(), null, EncounterType.OUTPATIENT, EncounterState.AWAITING_TRIAGE,
                payerType, 0, null, command.actorUserId());

        try {
            if (existing.isPresent()) {
                return openEncounterTransaction.runAfterCompletedVisit(toInsert, command.actorUserId());
            }
            return new CheckInResult(openEncounterTransaction.run(toInsert, command.actorUserId()), false);
        } catch (DuplicateKeyException raceLost) {
            // Two concurrent check-ins for the same patient both passed the
            // findOpenByPatientId check above; the partial unique index
            // caught the second insert, and that insert's transaction is
            // already rolled back at this point. 05-receptionist.md ⚠7:
            // "Attempting a second open encounter returns the existing
            // one ... rather than erroring" — recover by returning what the
            // other transaction committed instead of surfacing an error to
            // a receptionist who just double-clicked.
            return new CheckInResult(encounterRepository.findOpenByPatientId(command.patientId()).orElseThrow(() -> raceLost), true);
        }
    }

    private UUID facilityIdFor(UUID departmentId) {
        return scopeHierarchyPort.parentOf(ScopeRef.of(ScopeType.DEPARTMENT, departmentId))
                .orElseThrow(() -> new NoSuchElementException("Department not found: " + departmentId))
                .id();
    }
}
