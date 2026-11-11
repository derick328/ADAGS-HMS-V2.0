package com.adags.modules.encounters.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EncounterRepository {

    Optional<Encounter> findById(UUID id);

    /** The patient's open encounter, if any — state not in the terminal set (root CLAUDE.md "Concurrency"). */
    Optional<Encounter> findOpenByPatientId(UUID patientId);

    /** Locks the open visit while a completed visit is finalized during a new check-in. */
    Optional<Encounter> findOpenByPatientIdForUpdate(UUID patientId);

    /** 08-doctor-navigation.md §3.3: every encounter for this patient, across every clinician, newest first. */
    List<Encounter> findByPatientId(UUID patientId);

    /**
     * 05-receptionist.md §8's "here now" panel / dashboard tiles. {@code states}
     * null or empty means any state; {@code excludeTerminal} adds
     * "AND state NOT IN (terminal set)" on top of that; either bound null
     * means unbounded on that side.
     */
    List<Encounter> search(List<EncounterState> states, boolean excludeTerminal, Instant createdFrom, Instant createdTo);

    /** 06-triage.md §5.1's referral picker "queue depth" — patients already assigned to this provider, still waiting to be seen. */
    int countByAssignedProviderAndState(UUID assignedProviderId, EncounterState state);

    Encounter insert(Encounter encounter);

    /** Bumps version on every call — see backend/CLAUDE.md "Optimistic locking (@Version) on Encounter". */
    void updateState(UUID encounterId, EncounterState newState);

    /**
     * 06-triage.md §6 step 4: sets the referral, resolves a pending department
     * from the doctor's assignment, and sets EMERGENCY type when needed. Optimistically locked — throws
     * StaleEncounterException if the row's current version does not match
     * expectedVersion. Returns the row's new version on success.
     */
    int assignProviderAndType(UUID encounterId, UUID assignedProviderId, EncounterType type, int expectedVersion);

    void insertEvent(EncounterEvent event);
}
