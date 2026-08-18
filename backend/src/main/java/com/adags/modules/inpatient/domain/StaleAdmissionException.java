package com.adags.modules.inpatient.domain;

import java.util.UUID;

// root CLAUDE.md "Concurrency" / backend/CLAUDE.md "Optimistic locking
// (@Version) on ... Admission" — mirrors StaleEncounterException.
public class StaleAdmissionException extends RuntimeException {

    private final UUID admissionId;
    private final int expectedVersion;

    public StaleAdmissionException(UUID admissionId, int expectedVersion) {
        super("Admission " + admissionId + " has changed since it was last read (expected version "
                + expectedVersion + ") — reload and resubmit.");
        this.admissionId = admissionId;
        this.expectedVersion = expectedVersion;
    }

    public UUID admissionId() {
        return admissionId;
    }

    public int expectedVersion() {
        return expectedVersion;
    }
}
