package com.adags.modules.inpatient.domain;

import java.util.UUID;

// Root backend/CLAUDE.md "Concurrency": a double-tap must not chart two
// doses. The CAS UPDATE (WHERE status = 'DUE') finding zero rows is this
// exception — either already marked by a first request, or never existed.
public class MedicationAdministrationNotDueException extends RuntimeException {

    public MedicationAdministrationNotDueException(UUID id) {
        super("Medication administration slot is not DUE (already marked, or not found): " + id);
    }
}
