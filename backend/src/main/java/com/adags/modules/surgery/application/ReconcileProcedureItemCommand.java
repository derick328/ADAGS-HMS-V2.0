package com.adags.modules.surgery.application;

import java.util.UUID;

public record ReconcileProcedureItemCommand(
        UUID procedureItemId,
        int quantityUsed,
        int quantityReturned,
        int quantityWasted,
        String wastageReason,
        UUID reconciledBy
) {
}
