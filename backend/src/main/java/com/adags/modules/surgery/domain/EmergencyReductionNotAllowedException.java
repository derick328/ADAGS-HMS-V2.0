package com.adags.modules.surgery.domain;

import java.util.UUID;

// Covers both misuses of is_emergency_reduced: requested on SIGN_OUT
// ("SIGN_OUT is never reduced" — task text), or requested when the
// order's urgency is not IMMEDIATE (emergency-reduced mode without an
// emergency).
public class EmergencyReductionNotAllowedException extends RuntimeException {

    private final UUID surgeryOrderId;

    public EmergencyReductionNotAllowedException(UUID surgeryOrderId, String reason) {
        super("Emergency-reduced checklist not allowed: " + reason + " (surgery order " + surgeryOrderId + ")");
        this.surgeryOrderId = surgeryOrderId;
    }

    public UUID surgeryOrderId() {
        return surgeryOrderId;
    }
}
