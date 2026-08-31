-- Relax V68's original_code NOT NULL. V68 was too strict: 30+ existing
-- test fixtures INSERT INTO service_item (code, name, category) without
-- original_code, and unrelated non-importer paths (V15 seed, V32 ward
-- bed-day rows, direct pharmacy price seeding) also insert without it.
-- The importer path (JdbcCatalogItemLookup.insertNewCatalogItem) still
-- always populates original_code — that guarantee lives in code, not
-- in a table constraint that ends up rejecting legitimate non-importer
-- rows.
--
-- V68 already backfilled original_code = code on every pre-V68 row,
-- so the values that survived that migration stay populated. New
-- non-importer inserts land with original_code = NULL; UI/read paths
-- treat that as "not set", and admin browse still finds the row via
-- code / name / (nullable) original_code.

ALTER TABLE service_item
    ALTER COLUMN original_code DROP NOT NULL;
