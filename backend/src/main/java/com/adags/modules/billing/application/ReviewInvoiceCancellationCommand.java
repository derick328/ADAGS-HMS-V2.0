package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CancellationRequestStatus;

import java.util.UUID;

public record ReviewInvoiceCancellationCommand(UUID requestId, CancellationRequestStatus decision, UUID reviewedBy, String reviewNote) {
}
