package com.adags.modules.surgery.api;

import jakarta.validation.constraints.PositiveOrZero;

public record ReconcileProcedureItemRequest(
        @PositiveOrZero int quantityUsed,
        @PositiveOrZero int quantityReturned,
        @PositiveOrZero int quantityWasted,
        String wastageReason
) {
}
