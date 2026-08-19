package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// Phase 7 item 1: "Reduces what a patient owes without money moving. Used
// when a service was charged but not delivered, a price was wrong, or a
// goodwill adjustment is agreed." Never touches invoice_line — a separate
// offsetting record, so the original charge and invoice history stay
// intact. Same second-signature-above-threshold shape as InvoiceWriteOff,
// sharing that exact threshold (SecondSignatureThresholdResolver), not a
// separate one.
public record CreditNote(
        UUID id,
        UUID invoiceId,
        Money amount,
        String reason,
        String reference,
        UUID issuedBy,
        Instant issuedAt,
        boolean requiresSecondSignature,
        CreditNoteStatus status,
        UUID approvedBy,
        Instant approvedAt,
        String decisionNote
) {

    public static CreditNote issue(UUID invoiceId, Money amount, String reason, String reference, UUID issuedBy,
                                    boolean requiresSecondSignature) {
        if (amount.amountMinor() <= 0) {
            throw new IllegalArgumentException("Credit note amount must be > 0.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A credit note reason is required.");
        }
        return new CreditNote(null, invoiceId, amount, reason, reference, issuedBy, null,
                requiresSecondSignature, CreditNoteStatus.PENDING, null, null, null);
    }
}
