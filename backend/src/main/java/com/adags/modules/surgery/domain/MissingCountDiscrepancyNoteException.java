package com.adags.modules.surgery.domain;

import java.util.UUID;

// Same idiom as MissingWastageReasonException — a FALSE swab/instrument
// count is never silently recorded, it needs the note documenting what
// happened ("count_correct is NEVER waivable" — task text).
public class MissingCountDiscrepancyNoteException extends RuntimeException {

    private final UUID surgeryOrderId;

    public MissingCountDiscrepancyNoteException(UUID surgeryOrderId) {
        super("count_discrepancy_note is required when count_correct is false: surgery order " + surgeryOrderId);
        this.surgeryOrderId = surgeryOrderId;
    }

    public UUID surgeryOrderId() {
        return surgeryOrderId;
    }
}
