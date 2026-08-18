package com.adags.modules.clinical.domain;

// 06-triage.md §2.2: "Implausible values are rejected with a message
// naming the bound, never silently clamped."
public class ImplausibleVitalException extends RuntimeException {

    public ImplausibleVitalException(VitalType vitalType, double value, boolean belowBound, double bound) {
        super(vitalType + " value " + value + " is implausible: must be "
                + (belowBound ? ">= " + bound : "<= " + bound) + ".");
    }
}
