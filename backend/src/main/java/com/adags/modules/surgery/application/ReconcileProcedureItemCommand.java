package com.adags.modules.surgery.application;

import java.util.UUID;

public record ReconcileProcedureItemCommand(
        UUID procedureItemId,
        double quantityUsed,
        double quantityReturned,
        double quantityWasted,
        String wastageReason,
        UUID reconciledBy
) {
}
