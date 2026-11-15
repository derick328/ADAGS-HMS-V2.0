package com.adags.modules.inventory.application;

import java.util.UUID;

public record CreateLabStockRequestCommand(UUID itemId, double requestedQuantity, UUID requestingStoreId, UUID requestedBy, String requestNotes) {
}
