package com.adags.modules.reporting.application;

import java.time.LocalDate;

// 02-billing-architecture.md §13: "Three corrections that determine
// whether the numbers mean anything." collectedCash and insuranceBilled
// are period flows (bounded by periodFrom/periodTo); bypassDebt and
// depositLiability are point-in-time balances (as of now, regardless of
// the requested period) — a receivable and a liability are not
// meaningfully "earned between two dates" the way a settled payment is.
//
// ⚠ collectedCash and insuranceBilled are deliberately separate fields,
// never summed into one "revenue" total by this record or its
// consumers — "Revenue reporting must distinguish the two, or the cash
// position is fiction until remittances reconcile."
public record RevenueReport(
        LocalDate periodFrom,
        LocalDate periodTo,
        long collectedCashMinor,
        long insuranceBilledMinor,
        long emergencyBypassDebtMinor,
        // Always 0: no deposit ledger exists yet (07-doctor.md §11 —
        // BillingGateImpl.admissionDepositBacked is the same honest stub).
        // This is data that was never captured, not a figure this report
        // invents or omits silently.
        long depositLiabilityMinor,
        String currencyCode
) {
}
