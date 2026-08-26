package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.LabStockRequest;
import com.adags.modules.inventory.domain.LabStockRequestStatus;

import java.util.UUID;

public record LabStockRequestResponse(
        UUID id, UUID itemId, int requestedQuantity, int releasedQuantity, int remaining, UUID requestingStoreId,
        LabStockRequestStatus status, String responseNotes, String requestNotes
) {

    public static LabStockRequestResponse from(LabStockRequest request) {
        return new LabStockRequestResponse(request.id(), request.itemId(), request.requestedQuantity(),
                request.releasedQuantity(), request.remaining(), request.requestingStoreId(), request.status(),
                request.responseNotes(), request.requestNotes());
    }
}
