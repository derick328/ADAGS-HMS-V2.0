package com.adags.modules.inpatient.domain;

// 04-procedure-bedday-rules.md §3.4: "private wins the bed-day rate" —
// declaration order is the rank, GENERAL < PRIVATE, used by
// BedAssignment.create to decide whether a bed sits above the admission's
// ordered class.
public enum WardClass {
    GENERAL,
    PRIVATE;

    public boolean isAbove(WardClass ordered) {
        return this.ordinal() > ordered.ordinal();
    }
}
