package com.adags.modules.surgery.domain;

import java.util.UUID;

public class DispositionAlreadyRecordedException extends RuntimeException {

    private final UUID surgeryOrderId;

    public DispositionAlreadyRecordedException(UUID surgeryOrderId) {
        super("Disposition already recorded for surgery order " + surgeryOrderId);
        this.surgeryOrderId = surgeryOrderId;
    }

    public UUID surgeryOrderId() {
        return surgeryOrderId;
    }
}
