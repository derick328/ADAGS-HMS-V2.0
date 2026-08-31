package com.adags.modules.billing.domain;

// V67 catalog_import_batch.status transitions:
//   PREVIEWED -> APPLIED   (admin approves the preview)
//   PREVIEWED -> CANCELLED (admin walks away / a later reaper marks it)
// APPLIED and CANCELLED are terminal.
public enum CatalogImportStatus {
    PREVIEWED,
    APPLIED,
    CANCELLED
}
