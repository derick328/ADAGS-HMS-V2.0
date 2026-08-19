package com.adags.modules.insurance.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 02-billing-architecture.md §11: InsuranceClaim(invoice_id, scheme_id,
// claim_no, state, submitted_at, approved_minor, rejected_reason,
// batch_id).
//
// Phase 7 item 3 part 3: unlike Invoice's two-step draft()/issue() (a
// receptionist's typo shouldn't burn a number), a claim is raised only
// once an invoice actually reaches PATIENT_PORTION_SETTLED — there is no
// manual entry to protect against here, the same reasoning
// ChargePostingService gives for issuing system-posted charges
// immediately. claim_no is therefore allocated at the same moment the
// DRAFT row is created, not deferred to the SUBMITTED transition.
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

    public static InsuranceClaim raise(UUID invoiceId, UUID schemeId, String claimNo) {
        if (claimNo == null || claimNo.isBlank()) {
            throw new IllegalArgumentException("claimNo is required.");
        }
        return new InsuranceClaim(null, invoiceId, schemeId, claimNo, ClaimState.DRAFT, null, null, null, null, null);
    }
}
