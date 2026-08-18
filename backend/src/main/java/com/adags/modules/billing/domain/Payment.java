package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 02-billing-architecture.md §9.2. patientId, not invoiceId — a single
// payment's PaymentAllocation rows can cover lines across more than one
// invoice (§1 of the addendum: "the receptionist ticks which lines the
// money is for," lines, not one invoice), so the payment itself is
// scoped to the patient, and each allocation carries its own line.
public record Payment(
        UUID id,
        UUID patientId,
        UUID cashierShiftId,
        Money amount,
        PaymentMethod method,
        /** Required for every non-cash method (§9.2) — enforced by a DB CHECK. */
        String reference,
        PaymentStatus status,
        /** Unique — a repeat with the same key returns this same row, never a second payment. */
        String idempotencyKey,
        UUID receivedBy,
        Instant receivedAt
) {

    public static Payment receive(UUID patientId, UUID cashierShiftId, Money amount, PaymentMethod method,
                                   String reference, PaymentStatus status, String idempotencyKey, UUID receivedBy) {
        return new Payment(null, patientId, cashierShiftId, amount, method, reference, status, idempotencyKey,
                receivedBy, null);
    }
}
