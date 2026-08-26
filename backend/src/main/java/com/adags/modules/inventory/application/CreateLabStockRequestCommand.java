package com.adags.modules.inventory.application;

import java.util.UUID;

public record CreateLabStockRequestCommand(UUID itemId, int requestedQuantity, UUID requestingStoreId, UUID requestedBy, String requestNotes) {
}
