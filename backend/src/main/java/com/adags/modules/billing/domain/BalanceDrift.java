package com.adags.modules.billing.domain;

import java.util.UUID;

// One invoice whose materialized outstandingTotal disagrees with what the
// source ledger rows (payments, refunds, credit notes, write-offs) say it
// should be — 02-billing-architecture.md §9.3: "trust the derived value
// for display; trust the ledger for truth."
public record BalanceDrift(UUID invoiceId, long storedOutstandingMinor, long recomputedOutstandingMinor) {
}
