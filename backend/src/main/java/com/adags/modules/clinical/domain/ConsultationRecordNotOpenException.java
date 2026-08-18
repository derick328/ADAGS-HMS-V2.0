package com.adags.modules.clinical.domain;

import java.util.UUID;

// Autosave and finalize both only ever act on the OPEN record — 07-doctor.md
// §10: "FINALIZED is immutable... nobody un-finalizes." A stale client
// still autosaving after another tab finalized the same note must fail
// loudly, not silently overwrite a record that's supposed to be locked.
public class ConsultationRecordNotOpenException extends RuntimeException {

    public ConsultationRecordNotOpenException(UUID consultationRecordId) {
        super("ConsultationRecord " + consultationRecordId + " is not OPEN.");
    }
}
