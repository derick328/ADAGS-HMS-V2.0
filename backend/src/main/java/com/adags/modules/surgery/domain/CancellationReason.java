package com.adags.modules.surgery.domain;

import java.util.Set;

// 04-*.md §6. The billing treatment (CancelSurgeryOrderService) depends on
// which side caused the cancellation — these three groupings are the task
// text's own classification, not invented here:
//   patient-side:  after prep, items are still charged
//   hospital-side: after prep, items are waived AND voided
//   CLINICAL_DECISION: its own bucket, items waived (not "voided" too) —
//     kept separate rather than folded into hospital-side since the task
//     text lists it as a fourth, distinct branch.
public enum CancellationReason {
    PATIENT_REQUEST,
    PATIENT_UNFIT,
    NO_SHOW,
    SURGEON_UNAVAILABLE,
    THEATRE_UNAVAILABLE,
    EQUIPMENT_FAILURE,
    STAFF_UNAVAILABLE,
    CLINICAL_DECISION;

    private static final Set<CancellationReason> PATIENT_SIDE =
            Set.of(PATIENT_REQUEST, PATIENT_UNFIT, NO_SHOW);
    private static final Set<CancellationReason> HOSPITAL_SIDE =
            Set.of(SURGEON_UNAVAILABLE, THEATRE_UNAVAILABLE, EQUIPMENT_FAILURE, STAFF_UNAVAILABLE);

    public boolean isPatientSide() {
        return PATIENT_SIDE.contains(this);
    }

    public boolean isHospitalSide() {
        return HOSPITAL_SIDE.contains(this);
    }

    public boolean isClinicalDecision() {
        return this == CLINICAL_DECISION;
    }
}
