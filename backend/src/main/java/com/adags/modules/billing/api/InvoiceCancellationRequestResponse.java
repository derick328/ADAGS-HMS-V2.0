package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.CancellationRequestStatus;
import com.adags.modules.billing.domain.InvoiceCancellationRequest;

import java.time.Instant;
import java.util.UUID;

public record InvoiceCancellationRequestResponse(
        UUID id,
        UUID invoiceId,
        UUID requestedBy,
        String reason,
        Instant requestedAt,
        CancellationRequestStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        UUID supersedesRequestId
) {

    public static InvoiceCancellationRequestResponse from(InvoiceCancellationRequest request) {
        return new InvoiceCancellationRequestResponse(request.id(), request.invoiceId(), request.requestedBy(),
                request.reason(), request.requestedAt(), request.status(), request.reviewedBy(), request.reviewedAt(),
                request.reviewNote(), request.supersedesRequestId());
    }
}
