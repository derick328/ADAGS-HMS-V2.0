-- One-shot correction for prices the V67 catalogue importer stored 100x
-- too high before XlsxCatalogRowParser dropped its erroneous *100 step.
-- Root CLAUDE.md "Billing" convention: amount_minor for TZS is whole
-- shillings (V66 comment). The Excel PRICE column is already whole
-- shillings, so the buggy parser turned a file value of 5,000 into a
-- stored 500,000 — Kairuki_additions_40.xlsx landed every imported row
-- at that scale.
--
-- Scope of this migration:
--   * Only currently-open price entries (valid_to IS NULL) — historical
--     rows stay untouched.
--   * Only entries pointing at a V67-imported service_item (type IS
--     NOT NULL) — that column is set exclusively by the importer path
--     (JdbcCatalogItemLookup.insertNewCatalogItem), so we cannot hit
--     manually-created service_items here.
--   * Only rows where amount_minor is >= 100 AND evenly divisible by
--     100 — anything else is definitely not the "*100 inflated"
--     pattern, so we leave it alone.
--   * Only the CASH price list — the buggy importer only wrote CASH
--     entries.
--   * Close-then-insert versioning (root CLAUDE.md "Billing": "Close
--     valid_to, open a new row; never edit in place"). A March invoice
--     that already resolved March's inflated price still resolves it.
--
-- Two-statement structure: close the old rows first, then insert
-- corrected replacements — avoids racing the
-- idx_price_list_entry_current partial unique index inside a single
-- WITH ... data-modifying-CTE ... INSERT, whose statement-level
-- visibility semantics don't guarantee the UPDATE lands before the
-- INSERT's uniqueness check.
--
-- Post-V69, ApplyCatalogImportService inserts corrected values directly;
-- this migration is a one-time bootstrap for data that predates the fix.

CREATE TEMP TABLE inflated_open_entries ON COMMIT DROP AS
SELECT ple.id             AS entry_id,
       ple.price_list_id,
       ple.service_item_id,
       ple.amount_minor / 100 AS corrected_minor,
       ple.currency_code,
       ple.created_by
  FROM price_list_entry ple
  JOIN service_item si ON si.id = ple.service_item_id
  JOIN price_list pl ON pl.id = ple.price_list_id
 WHERE ple.valid_to IS NULL
   AND si.type IS NOT NULL
   AND pl.payer_category = 'CASH'
   AND ple.amount_minor >= 100
   AND ple.amount_minor % 100 = 0;

UPDATE price_list_entry ple
   SET valid_to = CURRENT_DATE
  FROM inflated_open_entries i
 WHERE ple.id = i.entry_id;

INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, currency_code, valid_from, created_by)
SELECT price_list_id, service_item_id, corrected_minor, currency_code, CURRENT_DATE, created_by
  FROM inflated_open_entries;
