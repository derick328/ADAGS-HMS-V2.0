package com.adags.modules.inpatient.domain;

import java.util.UUID;

public class ShiftHandoverAlreadyAcknowledgedException extends RuntimeException {

    public ShiftHandoverAlreadyAcknowledgedException(UUID handoverId) {
        super("Shift handover already acknowledged: " + handoverId);
    }
}
