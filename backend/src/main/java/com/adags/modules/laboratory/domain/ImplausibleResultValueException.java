package com.adags.modules.laboratory.domain;

// 15-lab-technician.md §5.3: "A potassium of 70 is a decimal error, not a
// finding." Rejected outright, naming the bound crossed — never saved,
// never flagged, mirrors clinical.domain.ImplausibleVitalException.
public class ImplausibleResultValueException extends RuntimeException {

    public ImplausibleResultValueException(String parameterCode, double value, boolean tooLow, double bound) {
        super("Value " + value + " for " + parameterCode + " is implausible ("
                + (tooLow ? "below " : "above ") + bound + ") — check for a data-entry error, not a finding.");
    }
}
