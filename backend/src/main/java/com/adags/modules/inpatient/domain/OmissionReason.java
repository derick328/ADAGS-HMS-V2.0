package com.adags.modules.inpatient.domain;

// 17-ward-nurse.md §5.4. DRUG_UNAVAILABLE notifies pharmacy automatically
// (MarkAdministrationService) — it means a ward stockout and nobody else
// is going to report it.
public enum OmissionReason {
    REFUSED_BY_PATIENT,
    NIL_BY_MOUTH,
    PATIENT_ABSENT,
    DRUG_UNAVAILABLE,
    CLINICAL_HOLD,
    VOMITED,
    IV_ACCESS_LOST,
    DUPLICATE_DOSE
}
