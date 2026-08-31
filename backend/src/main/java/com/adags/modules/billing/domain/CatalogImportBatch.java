package com.adags.modules.billing.domain;

import java.time.Instant;
import java.util.UUID;

// One catalog upload's audit envelope. storageKey names the archived
// .xlsx in MinIO (adags.minio.catalog-import-bucket); fileSha256 is the
// idempotency key that makes a byte-identical re-upload surface as a
// duplicate rather than silently double-import (V67 partial unique index
// on file_sha256 WHERE status != CANCELLED).
public record CatalogImportBatch(
        UUID id,
        String storageKey,
        String originalFilename,
        String fileSha256,
        CatalogImportStatus status,
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
}
