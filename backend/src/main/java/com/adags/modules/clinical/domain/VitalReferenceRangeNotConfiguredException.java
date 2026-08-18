package com.adags.modules.clinical.domain;

// Thrown instead of falling back to adult bounds — a silent fallback means
// every neonate is measured against adult bounds and either every one
// alerts or none do. This turns that invisible clinical error into a
// visible configuration gap (see ClinicalStartupChecks for the startup
// warning that lists these before any child is triaged).
public class VitalReferenceRangeNotConfiguredException extends RuntimeException {

    public VitalReferenceRangeNotConfiguredException(VitalType vitalType, AgeBand ageBand) {
        super("No configured VitalReferenceRange for " + vitalType + " / " + ageBand
                + " — a clinician must populate implausible/abnormal bounds for this band "
                + "before vitals can be recorded against it.");
    }
}
