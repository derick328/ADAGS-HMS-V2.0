package com.adags.modules.laboratory;

import java.util.UUID;

// Public surface other modules may depend on (root CLAUDE.md "Modules").
// UUID/String/primitive vocabulary only, never a laboratory.domain type,
// so callers (billing catalogue importer) never need to import across the
// module boundary. Same convention InventoryFacade documents.
public interface LaboratoryFacade {

    /**
     * V72 catalogue-import bootstrap: create a lab_test row for a
     * LABORATORY service_item that has none yet, so a doctor can order
     * it immediately after admin upload. specimen_type / container_type
     * land as placeholder "—", TAT defaults to 24h, verification level
     * NONE (matches LabTest defaults) — a lab lead refines each row
     * later through the lab admin surface. Idempotent: returns the
     * existing test id when the code is already taken, so a re-import
     * of the same catalogue file never duplicates.
     */
    UUID ensureCatalogueTest(String code, String name);
}
