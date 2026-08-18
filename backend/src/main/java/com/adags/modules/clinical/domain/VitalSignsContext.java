package com.adags.modules.clinical.domain;

// 06-triage.md §2.2 + 17-ward-nurse.md §4.1: VitalSigns is one shared table
// across the whole visit, not a per-screen table — three separate vitals
// tables was flagged there as a bug because nobody could see a patient's
// trend across a visit. context + contextRefId (see VitalSigns) is the
// polymorphic pointer that keeps it one table: TRIAGE points at a
// TriageAssessment, WARD_ROUNDS at an admission, POST_OP at a surgery.
// CONSULTATION's ref target is not built yet (no consultation record
// exists) — the value is reserved so the column never needs a later rename.
public enum VitalSignsContext {
    TRIAGE,
    CONSULTATION,
    WARD_ROUNDS,
    POST_OP
}
