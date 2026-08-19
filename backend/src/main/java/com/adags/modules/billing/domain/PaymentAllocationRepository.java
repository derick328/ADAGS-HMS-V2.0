package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAllocationRepository {

    PaymentAllocation insert(PaymentAllocation allocation);

    /** Only allocations funded by a SETTLED payment — a PENDING_CLEARANCE cheque contributes nothing yet. */
    List<PaymentAllocation> findSettledByInvoiceLineId(UUID invoiceLineId);

    Optional<PaymentAllocation> findById(UUID id);

    /** True when the allocation's funding payment is SETTLED — the eligibility check a refund requires. */
    boolean isFundedBySettledPayment(UUID paymentAllocationId);

    /**
     * Increments refundedMinor by amount, only up to the allocation's own
     * amount_minor — the WHERE clause (not a prior read) is the
     * concurrency guard (root CLAUDE.md "Concurrency"), so two refunds
     * racing against the same allocation can't together exceed what was
     * actually paid on it.
     *
     * @throws RefundExceedsAllocationException if amount exceeds what remains refundable at the moment of the update.
     */
    PaymentAllocation recordRefund(UUID paymentAllocationId, Money amount);
}
