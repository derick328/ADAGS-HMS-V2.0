package com.adags.modules.billing.domain;

// 02-billing-architecture.md §9.1: the cashier declares (OPEN -> CLOSED,
// counted cash entered), the accountant reconciles and signs off
// (CLOSED -> RECONCILED) — never the same person, enforced at the
// database (V17's CHECK (reconciled_by != closed_by)).
public enum CashierShiftStatus {
    OPEN,
    CLOSED,
    RECONCILED
}
