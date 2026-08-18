package com.adags.modules.clinical.domain;

// 07-doctor.md §10. OPEN is the one mutable phase (autosave writes to it
// every 20s); FINALIZED is immutable thereafter — a correction is a new
// row via amendsId, never an in-place edit. ABANDONED is the exit path for
// a consultation nobody can finish (09-doctor-incomplete-consultations.md
// §7) — modelled here per item 1's literal ask, though the abandon flow's
// own application service is a later item.
public enum ConsultationRecordStatus {
    OPEN,
    FINALIZED,
    ABANDONED
}
