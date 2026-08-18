package com.adags.modules.pharmacy.api;

import com.adags.shared.kernel.BatchAvailability;

import java.time.LocalDate;
import java.util.UUID;

public record BatchAvailabilityResponse(UUID batchId, String batchNumber, LocalDate expiryDate, int quantityAvailable) {

    public static BatchAvailabilityResponse from(BatchAvailability batch) {
        return new BatchAvailabilityResponse(batch.batchId(), batch.batchNumber(), batch.expiryDate(),
                batch.quantityAvailable());
    }
}
