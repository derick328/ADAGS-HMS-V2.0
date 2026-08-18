package com.adags.modules.encounters.domain;

// 05-receptionist.md §5.2: ordinary check-in always creates OUTPATIENT.
// EMERGENCY is set by the emergency fast path (⚠4, /patients/emergency) —
// not built this phase — and bypasses AWAITING_PAYMENT (05a-receptionist-
// decisions.md §2: "AWAITING_PAYMENT is bypassed when encounter.type =
// EMERGENCY"; root CLAUDE.md "Safety": IMMEDIATE triage priority bypasses
// the payment gate for the whole encounter).
public enum EncounterType {
    OUTPATIENT,
    EMERGENCY
}
