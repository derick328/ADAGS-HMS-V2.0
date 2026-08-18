package com.adags.modules.billing.domain;

// 02-billing-architecture.md §6.4/§10:
//   DRAFT -> ISSUED -> PATIENT_PORTION_SETTLED -> CLOSED
//   ISSUED -> CANCELLATION_PENDING -> VOID or back to ISSUED
// RECEIVABLE_POST_DISCHARGE is §8's outcome of an unpaid balance surviving
// EncounterClosed — never a blocker for that closure (root CLAUDE.md
// "Billing"), only a state the balance moves to afterwards.
//
// Only DRAFT and ISSUED have transition logic today (InvoiceRepository /
// ChargePostingService, Phase 3 item 2). The rest are modelled now so the
// CHECK constraint and this enum never need a later migration, the same
// reasoning as VitalReferenceRange's unconfigured paediatric bands —
// settlement (item 3, the gate), cancellation and the post-discharge
// sweep land in later items.
public enum InvoiceState {
    DRAFT,
    ISSUED,
    PATIENT_PORTION_SETTLED,
    CLOSED,
    CANCELLATION_PENDING,
    VOID,
    RECEIVABLE_POST_DISCHARGE
}
