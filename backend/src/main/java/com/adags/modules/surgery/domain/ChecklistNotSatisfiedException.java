package com.adags.modules.surgery.domain;

import java.util.UUID;

// 04-*.md §7: "Each pause BLOCKS the next stage" — the checklist row this
// stage needs (TIME_OUT for IN_PROGRESS, SIGN_OUT for COMPLETED, SIGN_IN
// for anaesthesia start) either doesn't exist yet, or (SIGN_OUT) exists
// but with count_correct still not TRUE.
public class ChecklistNotSatisfiedException extends RuntimeException {

    private final UUID surgeryOrderId;
    private final ChecklistPhase phase;

    public ChecklistNotSatisfiedException(UUID surgeryOrderId, ChecklistPhase phase) {
        super(phase + " checklist not satisfied for surgery order " + surgeryOrderId);
        this.surgeryOrderId = surgeryOrderId;
        this.phase = phase;
    }

    public UUID surgeryOrderId() {
        return surgeryOrderId;
    }

    public ChecklistPhase phase() {
        return phase;
    }
}
