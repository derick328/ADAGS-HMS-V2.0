package com.adags.modules.inpatient.domain;

// Null on Ward means unrestricted/mixed — not modelled as a third enum
// literal so "no restriction" can't be confused with a real (if unusual)
// third category.
public enum GenderRestriction {
    MALE,
    FEMALE
}
