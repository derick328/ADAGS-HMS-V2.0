package com.adags.modules.inventory.application;

import java.util.UUID;

public record ReleaseLabStockRequestCommand(UUID requestId, UUID batchId, double quantity, UUID reviewedBy) {
}
