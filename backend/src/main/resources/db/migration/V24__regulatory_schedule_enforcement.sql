-- Phase 5, pharmacy build brief item 2: regulatory schedule enforcement.
-- 14-controlled-medicines-tanzania.md §2/§3/§5/§6, 12-pharmacist.md §10.
--
-- D9 (confirmed): UNCLASSIFIED is dispensable — only CONTROLLED blocks.
-- RegulatorySchedule.isDispensable() already encodes this (build brief
-- item 1); this migration is the belt-and-braces database half of the
-- block, mirroring 13-controlled-medicines-policy.md §3's "one check is a
-- single point of failure... enforced at every layer that can move a
-- controlled item." The application/domain-layer half
-- (Item.assertDispensable(), called from RestockService/ActivateItemService
-- before every write) is the friendly-error half; these triggers are what
-- stops a future endpoint — or a bulk import, or a raw psql session — that
-- forgets to call it.

CREATE FUNCTION fn_block_controlled_item_activation() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.active AND NEW.regulatory_schedule = 'CONTROLLED' THEN
        RAISE EXCEPTION 'Item % is CONTROLLED and cannot be activated', NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_block_controlled_item_activation
    BEFORE INSERT OR UPDATE ON item
    FOR EACH ROW EXECUTE FUNCTION fn_block_controlled_item_activation();

CREATE FUNCTION fn_block_controlled_item_stock_batch() RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT regulatory_schedule FROM item WHERE id = NEW.item_id) = 'CONTROLLED' THEN
        RAISE EXCEPTION 'Item % is CONTROLLED and cannot receive stock', NEW.item_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_block_controlled_item_stock_batch
    BEFORE INSERT ON stock_batch
    FOR EACH ROW EXECUTE FUNCTION fn_block_controlled_item_stock_batch();

-- 12-pharmacist.md §10.5/13-*.md §2: every classification change is
-- audited — actor, timestamp, previous value, reason — permanently
-- (append-only, no update/delete path on the repository). The
-- CONTROLLED -> PRESCRIPTION_ONLY transition specifically is "the
-- transition that needs scrutiny" (build brief item 4); every row here
-- carries what a reviewer needs to check that judgment, not only that one.
CREATE TABLE item_classification_change (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id            UUID NOT NULL REFERENCES item(id),
    previous_schedule  TEXT NOT NULL,
    new_schedule       TEXT NOT NULL,
    reason             TEXT NOT NULL,
    changed_by         UUID NOT NULL REFERENCES app_user(id),
    changed_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_item_classification_change_item ON item_classification_change (item_id);
-- Build brief item 4: "report every CONTROLLED -> PRESCRIPTION_ONLY
-- reclassification" — this is the query the detection report runs.
CREATE INDEX idx_item_classification_change_controlled_release
    ON item_classification_change (changed_at)
    WHERE previous_schedule = 'CONTROLLED' AND new_schedule = 'PRESCRIPTION_ONLY';

-- 12-pharmacist.md §10.6, 13-*.md §6.1: the only safety net now that
-- UNCLASSIFIED is dispensable (D9) — items whose name fuzzy-matches a
-- known narcotic/limited-value-psychotropic INN. Seeded EMPTY, on purpose:
-- the authoritative First Schedule must come from the regulator via the
-- pharmacist-in-charge (14-*.md §2.1 / this build brief's explicit
-- instruction), never guessed into a migration. atc_prefix is nullable and
-- currently unused by any query — Item carries no ATC code in this build
-- brief's schema, so "match on... ATC prefix where available" is honestly
-- "not available yet" rather than silently wrong; the column exists so the
-- regulator's data (which does carry ATC codes) isn't dropped on the floor
-- once Item gains one.
--
-- ⚠ Curation discipline, not code: regulation 16(3) moderate/high-
-- therapeutic-value psychotropics (diazepam, midazolam, lorazepam,
-- phenobarbital, buprenorphine, ...) must NEVER be added here — see
-- 14-*.md §3.4. Nothing in this schema can enforce that without hardcoding
-- exactly the drug-name list this migration is deliberately not seeding;
-- it is the pharmacist-in-charge's responsibility when curating entries
-- from the regulator's source document.
CREATE TABLE narcotic_watchlist_entry (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inn_name         TEXT NOT NULL,
    atc_prefix       TEXT,
    source_document  TEXT NOT NULL,
    source_date      DATE NOT NULL,
    added_by         UUID NOT NULL REFERENCES app_user(id),
    added_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- pg_trgm already enabled by V13__clinical_reference_data.sql.
CREATE INDEX idx_narcotic_watchlist_entry_inn_trgm ON narcotic_watchlist_entry USING gin (inn_name gin_trgm_ops);

-- Same admin-tunable-without-a-redeploy convention as the patient
-- duplicate-detection thresholds (backend/CLAUDE.md) — read through
-- ProtocolSettingReader, not a constant.
INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'pharmacy.misclassification_detection.similarity_threshold', '0.4'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;
