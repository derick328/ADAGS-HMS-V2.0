package com.adags.modules.billing.application;

import java.io.InputStream;

// Port over MinIO. Archives the uploaded .xlsx keyed by the batch id so
// the Apply step re-parses deterministically and so an auditor can
// download the exact bytes that produced the change. Same pattern as the
// lab-machine-output storage adapter.
public interface CatalogFileStoragePort {

    /** Store the file. Returns the storage key persisted onto catalog_import_batch.storage_key. */
    String store(java.util.UUID batchId, String originalFilename, byte[] bytes);

    InputStream open(String storageKey);
}
