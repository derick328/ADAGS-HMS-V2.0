-- Phase 5/9 catalogue import: extends service_item with the vocabulary the
-- Excel file carries (TYPE, CATEGORY, CLASSIFICATION, external ITEM ID),
-- adds the trigram search index on name (the primary search vector per
-- the redesign brief), and introduces catalog_import_batch — the audit
-- envelope every bulk import goes through.
--
-- The existing service_item.category (CONSULTATION/LAB/PROCEDURE/PHARMACY)
-- stays: existing callers (BillingFacade, pharmacy's postServiceCharge)
-- read it, and rewriting them is out of scope for this phase. The new
-- 'type' column carries the finer Excel vocabulary; on new inserts the
-- importer derives 'category' from 'type' (see
-- CatalogImportService#deriveCategory). Existing rows' 'category' is
-- never overwritten on upsert — an admin re-classifying via Excel would
-- silently break consumers that key off the old vocabulary.
--
-- Whitelists live in a CHECK constraint (root CLAUDE.md "Data": the
-- constraint IS the control, not application-layer validation), so a
-- typo in the Excel file cannot slip through even if the importer's
-- validation were somehow bypassed.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Excel columns → service_item.
ALTER TABLE service_item
    ADD COLUMN type TEXT
        CHECK (type IN ('PHARMACY', 'PROCEDURE', 'SURGERY', 'LABORATORY',
                        'RADIOLOGY', 'OPTICAL', 'DIALYSIS', 'OTHERS',
                        'OTHER_GENERAL_CHARGES'));

ALTER TABLE service_item
    ADD COLUMN billing_category TEXT
        CHECK (billing_category IN ('SURGICAL_CHARGES', 'MEDICINES_AND_CONSUMABLES',
                                    'DIAGNOSTIC_EXAMINATIONS', 'PROCEDURAL_CHARGES',
                                    'INPATIENT_CHARGES', 'REGISTRATION_CONSULTATION_CHARGES',
                                    'OTHER_CHARGES', 'ADVANCE_PAYMENT'));

-- ~45% null in the source file (user spec) — no CHECK, no default. Loose
-- sub-classification, not required for grouping.
ALTER TABLE service_item
    ADD COLUMN classification TEXT;

-- Excel's ITEM ID — kept for traceability only, never a lookup key
-- (upsert is on 'code'). Not UNIQUE: source-side IDs can shift between
-- files (deletions in the source cause renumbering) and colliding with a
-- historical value must not fail an import.
ALTER TABLE service_item
    ADD COLUMN external_ref_id INTEGER;

-- Product name is the primary search vector (user spec §3). GIN trigram
-- index on lower(name) so the search endpoint's LIKE / similarity()
-- queries hit an index — same pattern as the pg_trgm work behind the
-- duplicate-patient detection thresholds (V7 protocol_setting).
CREATE INDEX idx_service_item_name_trgm ON service_item USING GIN (lower(name) gin_trgm_ops);

-- The audit envelope. Every catalogue import is either PREVIEWED (parsed,
-- reported to the admin, nothing written) or APPLIED (parsed + reported
-- + written in one transaction). Rejected uploads never reach this table
-- — the parser returns 400 and no batch row is created.
CREATE TABLE catalog_import_batch (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- MinIO object key (bucket configured under adags.minio.
    -- catalog-import-bucket). The uploaded .xlsx is archived so the apply
    -- step can re-parse deterministically and so an auditor can inspect
    -- the exact file that produced the changes.
    storage_key       TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    -- SHA-256 hex; a re-upload of the byte-identical file surfaces as a
    -- duplicate rather than a silent double-import.
    file_sha256       TEXT NOT NULL,
    /* status transitions:
         PREVIEWED  -> APPLIED  (admin clicks Apply on the summary)
         PREVIEWED  -> CANCELLED (admin walks away; expired by a later job)
       No other transitions exist. APPLIED and CANCELLED are terminal. */
    status            TEXT NOT NULL CHECK (status IN ('PREVIEWED', 'APPLIED', 'CANCELLED')),
    row_count         INTEGER NOT NULL CHECK (row_count >= 0),
    -- Counts computed at preview time and preserved. On APPLIED they must
    -- equal what actually happened (asserted by the service).
    new_item_count       INTEGER NOT NULL DEFAULT 0,
    price_change_count   INTEGER NOT NULL DEFAULT 0,
    metadata_change_count INTEGER NOT NULL DEFAULT 0,
    unchanged_count      INTEGER NOT NULL DEFAULT 0,
    uploaded_by       UUID NOT NULL REFERENCES app_user(id),
    uploaded_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_by        UUID REFERENCES app_user(id),
    applied_at        TIMESTAMPTZ,
    -- Second-person on apply (root CLAUDE.md "Authorisation"): while the
    -- uploader can also apply, a future policy tightening only needs a
    -- CHECK here to enforce two people.
    CHECK ((status = 'APPLIED') = (applied_by IS NOT NULL AND applied_at IS NOT NULL))
);

-- Idempotency: the same file bytes should surface a duplicate rather
-- than silently create two batches. Applies to non-cancelled batches
-- only — a cancelled preview shouldn't block a fresh upload of the same
-- file.
CREATE UNIQUE INDEX idx_catalog_import_batch_sha256_active
    ON catalog_import_batch (file_sha256)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_catalog_import_batch_uploaded_at ON catalog_import_batch (uploaded_at DESC);

-- Permission — ADMIN only, following the same SCREAMING_SNAKE convention
-- as every other row in `permission`. Not backfilled onto any other role.
INSERT INTO permission (code, module, description, is_clinical)
VALUES ('CATALOG_IMPORT', 'billing', 'Upload and apply a catalogue price file', FALSE);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM role r
  JOIN permission p ON p.code = 'CATALOG_IMPORT'
 WHERE r.code = 'ADMIN';
