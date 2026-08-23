package com.adags.modules.surgery.domain;

import java.util.UUID;

// Same idiom as pharmacy's PrescriptionLineNotOpenException /
// inventory's LabStockRequestNotOpenException: a well-formed request
// against a parent record that has already reached a terminal status
// (COMPLETED or CANCELLED) — nothing should still be getting prepared
// against a finished or cancelled surgery order.
public class SurgeryOrderNotOpenException extends RuntimeException {

    private final UUID surgeryOrderId;

    public SurgeryOrderNotOpenException(UUID surgeryOrderId, SurgeryOrderStatus status) {
        super("Surgery order " + surgeryOrderId + " is " + status + ", not open for preparation.");
        this.surgeryOrderId = surgeryOrderId;
    }

    public UUID surgeryOrderId() {
        return surgeryOrderId;
    }
}
