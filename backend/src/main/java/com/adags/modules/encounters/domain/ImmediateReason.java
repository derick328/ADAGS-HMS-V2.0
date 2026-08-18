package com.adags.modules.encounters.domain;

// 06-triage.md §4.1: the nurse picks a reason rather than acknowledging a
// plain confirmation — "a plain OK dialog attracts click-through blindness
// within a week." OTHER requires immediateReasonOtherText (enforced in
// TriageAssessment.create and by the immediate_reason_other_text CHECK in
// V11__triage_assessment.sql).
public enum ImmediateReason {
    THREATENED_OR_OBSTRUCTED_AIRWAY,
    SEVERE_RESPIRATORY_DISTRESS,
    SHOCK_OR_CIRCULATORY_COLLAPSE,
    UNRESPONSIVE_OR_RESPONDING_TO_PAIN_ONLY,
    ACTIVE_UNCONTROLLED_BLEEDING,
    ACTIVE_CONVULSION,
    OBSTETRIC_EMERGENCY,
    OTHER
}
