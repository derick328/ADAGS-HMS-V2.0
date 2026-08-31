package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.CatalogImportBatch;

import java.time.Instant;
import java.util.UUID;

public record CatalogImportBatchResponse(
        UUID id,
        String originalFilename,
        String status,
        int rowCount,
        int newItemCount,
        int priceChangeCount,
        int metadataChangeCount,
        int unchangedCount,
        UUID uploadedBy,
        Instant uploadedAt,
        UUID appliedBy,
        Instant appliedAt
) {
    public static CatalogImportBatchResponse from(CatalogImportBatch b) {
        return new CatalogImportBatchResponse(
                b.id(), b.originalFilename(), b.status().name(),
                b.rowCount(), b.newItemCount(), b.priceChangeCount(),
                b.metadataChangeCount(), b.unchangedCount(),
                b.uploadedBy(), b.uploadedAt(), b.appliedBy(), b.appliedAt());
    }
}
