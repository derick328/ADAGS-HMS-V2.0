package com.adags.modules.pharmacy.domain;

// 17-ward-nurse.md §5.1: "Doctor prescribes Ceftriaxone 1g IV, TDS, 5 days
// -> system generates 15 slots." timesPerDay is the interim, documented
// slot-count calibration GenerateMedicationScheduleService spaces evenly
// across 24h from — not a clinical dosing-schedule standard, the same
// honesty as backend/CLAUDE.md's duplicate-detection threshold comment.
// PRN (as-needed) generates no slots at all: administrations against a PRN
// line are recorded ad hoc, out of scope for the generated-schedule
// mechanism (documented gap, not a silent drop).
public enum PrescriptionFrequency {
    STAT(1),
    OD(1),
    BD(2),
    TDS(3),
    QDS(4),
    PRN(0),
    OTHER(0);

    private final int timesPerDay;

    PrescriptionFrequency(int timesPerDay) {
        this.timesPerDay = timesPerDay;
    }

    public int timesPerDay() {
        return timesPerDay;
    }

    /** STAT is a single one-off dose — duration_days is ignored even if supplied. */
    public boolean isSingleDose() {
        return this == STAT;
    }
}
