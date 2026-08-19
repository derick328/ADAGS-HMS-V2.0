package com.adags.modules.billing.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RequestInvoiceWriteOffRequest(@Min(1) long amountMinor, @NotBlank String reason) {
}
