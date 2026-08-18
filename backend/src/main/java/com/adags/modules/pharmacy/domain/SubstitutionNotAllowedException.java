package com.adags.modules.pharmacy.domain;

import java.util.UUID;

// 12-pharmacist.md §5.1: "The prescriber's substitution_allowed flag
// governs whether a proposal is possible at all."
public class SubstitutionNotAllowedException extends RuntimeException {

    private final UUID prescriptionLineId;

    public SubstitutionNotAllowedException(UUID prescriptionLineId, String reason) {
        super("Prescription line " + prescriptionLineId + " cannot be substituted: " + reason + ".");
        this.prescriptionLineId = prescriptionLineId;
    }

    public UUID prescriptionLineId() {
        return prescriptionLineId;
    }
}
