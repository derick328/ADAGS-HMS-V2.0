package com.adags.modules.billing.api;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record RequestInvoiceCancellationRequest(@NotBlank String reason, UUID supersedesRequestId) {
}
