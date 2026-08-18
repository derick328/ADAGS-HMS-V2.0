package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.domain.BatchAllocation;

import java.util.UUID;

public record BatchAllocationResponse(UUID batchId, int quantity) {

    public static BatchAllocationResponse from(BatchAllocation allocation) {
        return new BatchAllocationResponse(allocation.batchId(), allocation.quantity());
    }
}
