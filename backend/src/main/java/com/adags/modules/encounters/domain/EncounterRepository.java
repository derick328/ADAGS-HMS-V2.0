package com.adags.modules.encounters.domain;

import java.util.Optional;
import java.util.UUID;

public interface EncounterRepository {

    Optional<Encounter> findById(UUID id);

    /** The patient's open encounter, if any — state not in the terminal set (root CLAUDE.md "Concurrency"). */
    Optional<Encounter> findOpenByPatientId(UUID patientId);

    Encounter insert(Encounter encounter);

    /** Bumps version on every call — see backend/CLAUDE.md "Optimistic locking (@Version) on Encounter". */
    void updateState(UUID encounterId, EncounterState newState);

    /**
     * 06-triage.md §6 step 4: sets the referral and, for an EMERGENCY
     * escalation, the encounter type. Optimistically locked — throws
     * StaleEncounterException if the row's current version does not match
     * expectedVersion. Returns the row's new version on success.
     */
    int assignProviderAndType(UUID encounterId, UUID assignedProviderId, EncounterType type, int expectedVersion);

    void insertEvent(EncounterEvent event);
}
