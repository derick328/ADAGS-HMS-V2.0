package com.adags.modules.surgery.domain;

import java.util.UUID;

// 04-*.md §4: "A procedure requiring anaesthesia cannot reach COMPLETED
// without either this pointer or an explicit no_anaesthesia flag."
public class AnaesthesiaNotRecordedException extends RuntimeException {

    private final UUID surgeryOrderId;

    public AnaesthesiaNotRecordedException(UUID surgeryOrderId) {
        super("Surgery order " + surgeryOrderId
                + " has no anaesthesia paper record (pointer or explicit no_anaesthesia declaration).");
        this.surgeryOrderId = surgeryOrderId;
    }

    public UUID surgeryOrderId() {
        return surgeryOrderId;
    }
}
