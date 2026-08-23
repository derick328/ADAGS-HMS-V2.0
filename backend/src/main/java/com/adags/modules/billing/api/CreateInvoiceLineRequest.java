package com.adags.modules.billing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateInvoiceLineRequest(@NotBlank String serviceItemCode, @Positive int quantity) {
}
