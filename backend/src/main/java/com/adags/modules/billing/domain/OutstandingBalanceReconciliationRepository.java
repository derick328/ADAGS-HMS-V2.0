package com.adags.modules.billing.domain;

import java.util.List;
import java.util.UUID;

// 02-billing-architecture.md §9.3's formula, made real: recomputes each
// non-DRAFT invoice's outstanding total from source rows (settled
// payments, refunds, credit notes, write-offs) and compares it against
// the materialized column every other operation in this module writes
// directly. Separate from InvoiceRepository — a reconciliation concern,
// not a write-side entity operation.
public interface OutstandingBalanceReconciliationRepository {

    /** Every invoice whose stored outstandingTotal disagrees with the source rows. */
    List<BalanceDrift> findDrifted();

    /** Corrects one invoice's outstandingTotal to the recomputed value. */
    void correct(UUID invoiceId, long recomputedOutstandingMinor);
}
