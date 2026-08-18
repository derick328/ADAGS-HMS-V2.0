package com.adags.modules.encounters.domain;

import java.util.UUID;

// root CLAUDE.md "Concurrency": a real constraint, not a read-then-write
// application check — the version predicate lives in the UPDATE itself
// (JdbcEncounterRepository.assignProviderAndType's "WHERE version =
// :expectedVersion"). This models what that predicate matching zero rows
// means: a second nurse submitting against a stale read gets a reload
// prompt (backend/CLAUDE.md "API": 409 Conflict with the current version),
// never a silent overwrite of whatever changed the row first.
public class StaleEncounterException extends RuntimeException {

    private final UUID encounterId;
    private final int expectedVersion;

    public StaleEncounterException(UUID encounterId, int expectedVersion) {
        super("Encounter " + encounterId + " has changed since it was last read (expected version "
                + expectedVersion + ") — reload and resubmit.");
        this.encounterId = encounterId;
        this.expectedVersion = expectedVersion;
    }

    public UUID encounterId() {
        return encounterId;
    }

    public int expectedVersion() {
        return expectedVersion;
    }
}
