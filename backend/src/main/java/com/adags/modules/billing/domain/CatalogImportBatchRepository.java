package com.adags.modules.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogImportBatchRepository {

    CatalogImportBatch insert(CatalogImportBatch batch);

    Optional<CatalogImportBatch> findById(UUID id);

    /**
     * Idempotency guard for uploads. Returns the row if a byte-identical
     * file is currently PREVIEWED or APPLIED — a fresh upload of the same
     * file must not create a second batch (V67 partial unique index).
     */
    Optional<CatalogImportBatch> findActiveByFileSha256(String sha256);

    /** Most recent first. Small page — the admin skims. */
    List<CatalogImportBatch> listRecent(int limit);

    /**
     * Marks the batch APPLIED, records who and when, freezes the change
     * counts. Called from ApplyCatalogImportService inside the same
     * transaction as the service_item / price_list_entry writes.
     */
    CatalogImportBatch markApplied(UUID batchId, UUID appliedBy, int newItemCount,
                                   int priceChangeCount, int metadataChangeCount, int unchangedCount);

    /** Manual admin cancel, or a background reaper for stale previews. */
    CatalogImportBatch markCancelled(UUID batchId);
}
