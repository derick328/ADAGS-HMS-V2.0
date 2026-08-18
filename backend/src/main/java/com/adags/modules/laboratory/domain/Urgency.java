package com.adags.modules.laboratory.domain;

// 15-lab-technician.md §3.2: "ORDER BY ... urgency ASC -- STAT, URGENT, ROUTINE."
// Declared in that priority order so the natural enum ordinal sorts correctly.
public enum Urgency {
    STAT,
    URGENT,
    ROUTINE
}
