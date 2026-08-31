-- Phase 5/9 catalogue import — support source files where PRODUCT CODE is
-- reused across many unrelated items (Kairuki.xlsx: DIS0001 covers 175
-- disparate disposables; RAD0001 covers 10 different radiology items;
-- and 10 more codes with lower multiplicity). Root CLAUDE.md "Data" +
-- V15's UNIQUE (code) mean the storage-side code must stay unique, so
-- the importer disambiguates in-file collisions by suffixing the second
-- and later occurrences (see CatalogCodeResolver) — that generated code
-- goes into service_item.code, and the raw source value goes here.
--
-- Design choice: original_code is separate from code (not a replacement).
-- Downstream lookups (BillingFacade, pharmacy postServiceCharge, lab
-- postCharge, surgery_order.procedure_service_item_code) all resolve by
-- code; keeping code the stable business key preserves those invariants.
-- original_code exists purely so the admin UI and audit can trace a row
-- back to what the spreadsheet actually said.
--
-- NOT NULL after backfill: every service_item has some source code, and
-- new inserts always populate it. Not UNIQUE — that is the whole point.

ALTER TABLE service_item
    ADD COLUMN original_code TEXT;

UPDATE service_item
   SET original_code = code
 WHERE original_code IS NULL;

ALTER TABLE service_item
    ALTER COLUMN original_code SET NOT NULL;

-- The admin browse can filter by original_code so "show me every row the
-- source labels DIS0001" answers in one query rather than a table scan.
CREATE INDEX idx_service_item_original_code ON service_item (original_code);
