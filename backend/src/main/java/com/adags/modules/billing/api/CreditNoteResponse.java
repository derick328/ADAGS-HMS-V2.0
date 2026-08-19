package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.CreditNote;
import com.adags.modules.billing.domain.CreditNoteStatus;

import java.time.Instant;
import java.util.UUID;

public record CreditNoteResponse(
        UUID id,
        UUID invoiceId,
        long amountMinor,
        String currencyCode,
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

    public static CreditNoteResponse from(CreditNote creditNote) {
        return new CreditNoteResponse(creditNote.id(), creditNote.invoiceId(), creditNote.amount().amountMinor(),
                creditNote.amount().currencyCode(), creditNote.reason(), creditNote.reference(), creditNote.issuedBy(),
                creditNote.issuedAt(), creditNote.requiresSecondSignature(), creditNote.status(),
                creditNote.approvedBy(), creditNote.approvedAt(), creditNote.decisionNote());
    }
}
