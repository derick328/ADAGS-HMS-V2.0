package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 03-billing-addendum.md §1: PaymentAllocation(payment_id, invoice_line_id,
// amount_minor) — the receptionist's line picker. Exact-match validation
// (allocated == line.patient_portion, unless an active bypass allows
// partial) lives in PostPaymentService, not here; this record is just the
// resulting fact.
//
// refundedMinor (V44): the materialized running total of APPLIED refunds
// against this allocation — same "guard in the UPDATE's WHERE clause, not
// a prior read" discipline as Invoice.outstandingTotal, so two refunds
// racing against the same allocation can't together exceed what was
// actually paid on it. Always 0 at creation; only PaymentAllocationRepository
// .recordRefund ever moves it.
public record PaymentAllocation(
        UUID id,
        UUID paymentId,
        UUID invoiceLineId,
        Money amount,
        long refundedMinor,
        Instant createdAt
) {

    public static PaymentAllocation allocate(UUID paymentId, UUID invoiceLineId, Money amount) {
        return new PaymentAllocation(null, paymentId, invoiceLineId, amount, 0, null);
    }
}
