package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.LabStockRequestStatus;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §8: the pharmacist's Lab Stock Requests review queue —
// every open request across every lab technician, with the item name a
// bare LabStockRequestResponse doesn't carry (that record backs the
// requester's own "mine" list, where the requester already knows what
// they asked for).
public record LabStockRequestQueueRowResponse(
        UUID id,
        UUID itemId,
        String itemCode,
        String itemName,
        int requestedQuantity,
        int releasedQuantity,
        int remaining,
        UUID requestingStoreId,
        UUID requestedBy,
        String requestedByUsername,
        Instant requestedAt,
        LabStockRequestStatus status,
        String requestNotes
) {
}
