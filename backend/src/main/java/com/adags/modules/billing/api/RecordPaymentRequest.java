package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

// 03-billing-addendum.md §1: "the payment screen is a line picker" —
// allocations is that tick list, the total is derived from it, never sent
// separately. idempotencyKey is client-generated per attempt (root
// CLAUDE.md "Concurrency": "a double-tap must not charge twice").
public record RecordPaymentRequest(
        @NotNull UUID patientId,
        @NotNull PaymentMethod method,
        /** Required for every non-cash method — the DB CHECK is the real guard. */
        String reference,
        @NotBlank String idempotencyKey,
        @NotEmpty List<@Valid PaymentLineAllocationRequest> allocations
) {
}
