package com.adags.modules.surgery.api;

import jakarta.validation.constraints.PositiveOrZero;

public record ReconcileProcedureItemRequest(
        @PositiveOrZero @jakarta.validation.constraints.Digits(integer = 14, fraction = 6) double quantityUsed,
        @PositiveOrZero @jakarta.validation.constraints.Digits(integer = 14, fraction = 6) double quantityReturned,
        @PositiveOrZero @jakarta.validation.constraints.Digits(integer = 14, fraction = 6) double quantityWasted,
        String wastageReason
) {
}
