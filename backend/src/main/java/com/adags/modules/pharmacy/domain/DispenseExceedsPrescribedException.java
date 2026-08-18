package com.adags.modules.pharmacy.domain;

import java.util.UUID;

// 12-pharmacist.md §4.4: "dispensed_total > prescribed -> rejected, hard block."
public class DispenseExceedsPrescribedException extends RuntimeException {

    private final UUID prescriptionLineId;

    public DispenseExceedsPrescribedException(UUID prescriptionLineId, int requested, int remaining) {
        super("Prescription line " + prescriptionLineId + ": cannot dispense " + requested
                + " against " + remaining + " remaining.");
        this.prescriptionLineId = prescriptionLineId;
    }

    public UUID prescriptionLineId() {
        return prescriptionLineId;
    }
}
