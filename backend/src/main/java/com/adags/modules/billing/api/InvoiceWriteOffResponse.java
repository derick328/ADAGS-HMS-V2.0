package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.InvoiceWriteOff;
import com.adags.modules.billing.domain.WriteOffStatus;

import java.time.Instant;
import java.util.UUID;

public record InvoiceWriteOffResponse(
        UUID id,
        UUID invoiceId,
        long amountMinor,
        String currencyCode,
        String reason,
        UUID requestedBy,
        Instant requestedAt,
        boolean requiresSecondSignature,
        WriteOffStatus status,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote
) {

    public static InvoiceWriteOffResponse from(InvoiceWriteOff writeOff) {
        return new InvoiceWriteOffResponse(writeOff.id(), writeOff.invoiceId(), writeOff.amount().amountMinor(),
                writeOff.amount().currencyCode(), writeOff.reason(), writeOff.requestedBy(), writeOff.requestedAt(),
                writeOff.requiresSecondSignature(), writeOff.status(), writeOff.decidedBy(), writeOff.decidedAt(),
                writeOff.decisionNote());
    }
}
