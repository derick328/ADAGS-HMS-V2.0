package com.adags.modules.pharmacy.domain;

import java.util.UUID;

// Thrown when marking UNAVAILABLE, cancelling for substitution, or
// proposing a substitution against a line that is already terminal
// (COMPLETED, CANCELLED, or UNAVAILABLE) — a dispensed or already-resolved
// line has nothing left to substitute or mark unavailable.
public class PrescriptionLineNotOpenException extends RuntimeException {

    private final UUID prescriptionLineId;

    public PrescriptionLineNotOpenException(UUID prescriptionLineId, PrescriptionLineStatus status) {
        super("Prescription line " + prescriptionLineId + " is " + status + " — not open for this action.");
        this.prescriptionLineId = prescriptionLineId;
    }

    public UUID prescriptionLineId() {
        return prescriptionLineId;
    }
}
