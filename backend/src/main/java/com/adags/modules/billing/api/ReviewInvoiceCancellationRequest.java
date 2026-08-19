package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.CancellationRequestStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewInvoiceCancellationRequest(@NotNull CancellationRequestStatus decision, String reviewNote) {
}
