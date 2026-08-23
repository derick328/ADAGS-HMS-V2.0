package com.adags.modules.surgery.domain;

import java.util.UUID;

// 04-procedure-bedday-rules.md §2.4: "Procedure COMPLETE -> disposition" —
// disposition is only meaningful once the procedure itself is done.
public class SurgeryOrderNotCompletedException extends RuntimeException {

    private final UUID surgeryOrderId;

    public SurgeryOrderNotCompletedException(UUID surgeryOrderId) {
        super("Surgery order " + surgeryOrderId + " is not COMPLETED — disposition cannot be recorded yet.");
        this.surgeryOrderId = surgeryOrderId;
    }

    public UUID surgeryOrderId() {
        return surgeryOrderId;
    }
}
