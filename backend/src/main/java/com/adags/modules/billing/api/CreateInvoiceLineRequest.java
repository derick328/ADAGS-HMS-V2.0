package com.adags.modules.billing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateInvoiceLineRequest(@NotBlank String serviceItemCode, @Positive @jakarta.validation.constraints.Digits(integer = 14, fraction = 6) double quantity) {
}
