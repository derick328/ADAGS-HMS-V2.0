package com.adags.modules.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AddWatchlistEntryRequest(
        @NotBlank String innName,
        String atcPrefix,
        @NotBlank String sourceDocument,
        @NotNull LocalDate sourceDate
) {
}
