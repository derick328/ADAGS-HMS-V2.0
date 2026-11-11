package com.adags.modules.billing.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateCatalogPriceRequest(
        @NotNull @PositiveOrZero Long expectedPriceMinor,
        @NotNull @PositiveOrZero Long newPriceMinor
) {}
