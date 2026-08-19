package com.adags.modules.insurance.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 02-billing-architecture.md §11: InsuranceClaim(invoice_id, scheme_id,
// claim_no, state, submitted_at, approved_minor, rejected_reason,
// batch_id). Schema only in this part — draft() is the only lifecycle
// operation built now; claim_no is allocated from the gapless sequence
// only at actual submission (a later part), same two-step pattern as
// Invoice.draft()/issue().
public record InsuranceClaim(
        UUID id,
        UUID invoiceId,
        UUID schemeId,
        String claimNo,
        ClaimState state,
        Instant submittedAt,
        Money approvedAmount,
        String rejectedReason,
        UUID batchId,
        Instant createdAt
) {

    public static InsuranceClaim draft(UUID invoiceId, UUID schemeId) {
        return new InsuranceClaim(null, invoiceId, schemeId, null, ClaimState.DRAFT, null, null, null, null, null);
    }
}
