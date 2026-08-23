package com.adags.modules.billing.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record PaymentLineAllocationRequest(@NotNull UUID invoiceLineId, @Positive long amountMinor) {
}
