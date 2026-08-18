package com.adags.modules.inpatient.domain;

// 17-ward-nurse.md §8.2: "does not survive a multi-day admission across
// three shifts a day."
public enum Shift {
    MORNING,
    AFTERNOON,
    NIGHT
}
