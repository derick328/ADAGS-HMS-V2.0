package com.adags.modules.surgery.domain;

import java.util.UUID;

// Same idiom as encounters.application.InvalidEncounterTransitionException —
// the repository's WHERE-guarded status UPDATE is the real guard; this is
// what a rejected transition attempt gets, not a 500.
public class InvalidSurgeryOrderTransitionException extends RuntimeException {

    private final UUID surgeryOrderId;
    private final SurgeryOrderStatus from;
    private final SurgeryOrderStatus to;

    public InvalidSurgeryOrderTransitionException(UUID surgeryOrderId, SurgeryOrderStatus from, SurgeryOrderStatus to) {
        super("Surgery order " + surgeryOrderId + " cannot move from " + from + " to " + to + ".");
        this.surgeryOrderId = surgeryOrderId;
        this.from = from;
        this.to = to;
    }

    public UUID surgeryOrderId() {
        return surgeryOrderId;
    }

    public SurgeryOrderStatus from() {
        return from;
    }

    public SurgeryOrderStatus to() {
        return to;
    }
}
