package com.adags.modules.surgery.domain;

// 04-procedure-bedday-rules.md / root CLAUDE.md "Billing": IMMEDIATE is the
// urgency value that bypasses the payment gate for the whole encounter
// (ScheduleSurgeryService) — same semantics as TriagePriority.IMMEDIATE.
public enum SurgeryUrgency {
    ELECTIVE,
    URGENT,
    IMMEDIATE
}
