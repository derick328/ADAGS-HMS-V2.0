package com.adags.modules.clinical.domain;

// 07-doctor.md §6: where an AllergyRecord entry came from. TRIAGE is the
// first writer (06-triage.md §2.4/§6 step 3); DOCTOR is this phase's
// reconciliation pass against it (item 5). PHARMACIST/PATIENT_REPORTED
// have no writer yet — modelled now so the column never needs a later
// rename, same reasoning as VitalSignsContext's CONSULTATION value.
public enum AllergySource {
    TRIAGE,
    DOCTOR,
    PHARMACIST,
    PATIENT_REPORTED
}
