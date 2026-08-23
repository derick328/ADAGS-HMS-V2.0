package com.adags.modules.billing.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CloseCashierShiftRequest(@NotNull @PositiveOrZero Long countedCashMinor) {
}
