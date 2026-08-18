package com.adags.modules.inpatient.domain;

// 17-ward-nurse.md §1 ⚠2's resolution table + this repo's Phase 6 brief:
// required whenever a BedAssignment places a patient above their admission's
// ordered ward class — private wins the bed-day rate and the patient finds
// out at the desk otherwise, so the reason is recorded, not silently applied.
public enum PlacementReason {
    NO_BED_IN_ORDERED_CLASS,
    CLINICAL_NEED,
    PATIENT_REQUEST,
    OTHER
}
