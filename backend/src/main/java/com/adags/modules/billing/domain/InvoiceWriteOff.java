package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 18-admin.md (Phase 7 item 1) / 02-billing-architecture.md §9.3:
// write_offs is a component of Invoice.outstandingTotal. requiresSecondSignature
// is decided once, at request time, from the threshold in effect then — a
// later threshold change must not rewrite what an already-pending write-off
// requires (same reasoning as the price snapshot on PharmacyItemProposal).
//
// Below the threshold, the requester's own action is the required signature:
// RequestInvoiceWriteOffService applies it immediately and decides it with
// decidedBy == requestedBy — the one case the DB CHECK permits that. Above
// it, the request lands PENDING for a second, distinct accountant to decide.
public record InvoiceWriteOff(
        UUID id,
        UUID invoiceId,
        Money amount,
        String reason,
        UUID requestedBy,
        Instant requestedAt,
        boolean requiresSecondSignature,
        WriteOffStatus status,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote
) {

    public static InvoiceWriteOff request(UUID invoiceId, Money amount, String reason, UUID requestedBy,
                                           boolean requiresSecondSignature) {
        if (amount.amountMinor() <= 0) {
            throw new IllegalArgumentException("Write-off amount must be > 0.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A write-off reason is required.");
        }
        return new InvoiceWriteOff(null, invoiceId, amount, reason, requestedBy, null,
                requiresSecondSignature, WriteOffStatus.PENDING, null, null, null);
    }
}
