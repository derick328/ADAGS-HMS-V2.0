-- Phase 2, clinical core, item 4: clinical reference data.
-- docs/spec/10-clinical-reference-data.md §2, §5.1. Package is
-- `referencedata` (backend/CLAUDE.md) — the spec's own module name,
-- "clinical-reference", isn't a legal Java package segment.
--
-- Deliberately NOT seeded here. The WHO ICD-10 release is external,
-- licensed data with no citable subset available in this environment —
-- §4.1's staged approach is: import the full release with an empty common
-- list, run a few weeks logging real searches (reference_search_log
-- below), then build the common list and synonym set from what clinicians
-- actually typed and selected. Icd10ImportService is the import path this
-- table is built for; seeding it is an operational task for the clinical
-- lead, not a step in this migration.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- §2.1: code is the natural key, not a UUID — it IS the interoperable
-- identifier clinicians and insurers already use. parent_code's FK is
-- DEFERRABLE: a bulk import (Icd10ImportService) upserts one release's
-- worth of rows inside a single transaction, and nothing guarantees a
-- parent code appears before its children in the source data — deferring
-- the check to commit time means import order doesn't matter, only that
-- every referenced parent exists by the time the transaction ends.
CREATE TABLE icd10_code (
    code            TEXT PRIMARY KEY,
    title           TEXT NOT NULL,
    chapter         TEXT,
    block           TEXT,
    parent_code     TEXT REFERENCES icd10_code(code) DEFERRABLE INITIALLY DEFERRED,
    icd10_release   TEXT NOT NULL,
    -- ⚠ some ICD-10 codes are category headers ('B50') rather than usable
    -- diagnoses ('B50.9') — offering them produces claims insurers reject.
    is_billable     BOOLEAN NOT NULL DEFAULT TRUE,
    -- ⚠ never deleted (root CLAUDE.md "Data") — a 2026 diagnosis must
    -- remain readable against a code retired in 2027. Retirement sets this
    -- false, which removes it from search while leaving it resolvable.
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- §5.1: trigram for fuzzy/partial title matching, prefix index for a
-- clinician who types the code directly. Both paths must work.
CREATE INDEX idx_icd10_code_title_trgm ON icd10_code USING gin (title gin_trgm_ops);
CREATE INDEX idx_icd10_code_code_pattern ON icd10_code (code text_pattern_ops);
CREATE INDEX idx_icd10_code_parent ON icd10_code (parent_code) WHERE parent_code IS NOT NULL;
CREATE INDEX idx_icd10_code_active_billable ON icd10_code (is_active, is_billable);

-- §2.2: department-scoped common lists with a hospital-wide fallback
-- (department_id NULL). Resolution order at search time — this
-- department's common codes, then hospital-wide, then full active/billable
-- ICD-10 — becomes ranking weight in Icd10SearchRepository (§5.2: 500 -
-- sort_rank for department, 300 - sort_rank hospital-wide).
CREATE TABLE common_code (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          TEXT NOT NULL REFERENCES icd10_code(code),
    department_id UUID REFERENCES department(id),
    sort_rank     INTEGER NOT NULL DEFAULT 0,
    added_by      UUID NOT NULL REFERENCES app_user(id),
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE
);

-- One active row per (code, department) — the same NULL-department partial
-- unique pattern V7__protocol_setting.sql uses: a plain UNIQUE(code,
-- department_id) would treat every NULL department as distinct and admit
-- unlimited duplicate hospital-wide entries for the same code.
CREATE UNIQUE INDEX idx_common_code_dept ON common_code (code, department_id)
    WHERE department_id IS NOT NULL AND is_active;
CREATE UNIQUE INDEX idx_common_code_global ON common_code (code)
    WHERE department_id IS NULL AND is_active;
CREATE INDEX idx_common_code_department ON common_code (department_id, is_active, sort_rank);

-- §2.3/§3: propose-and-approve, mirroring the invoice-cancellation
-- pattern — "An incorrect synonym silently produces wrong diagnoses...
-- nothing in the interface reveals the error", so only APPROVED synonyms
-- ever reach search (Icd10SearchRepository).
CREATE TABLE icd10_synonym (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          TEXT NOT NULL REFERENCES icd10_code(code),
    term          TEXT NOT NULL,
    language      TEXT NOT NULL CHECK (language IN ('sw', 'en', 'local')),
    status        TEXT NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED')),
    proposed_by   UUID NOT NULL REFERENCES app_user(id),
    proposed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by   UUID REFERENCES app_user(id),
    reviewed_at   TIMESTAMPTZ,
    review_note   TEXT,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    -- root CLAUDE.md "Authorisation": "Second-person constraints at the
    -- database" — the same pattern as elevation_request.reviewed_by !=
    -- requested_by, applied here because approving your own proposed
    -- synonym defeats the entire point of a second reviewer.
    CHECK (reviewed_by IS NULL OR reviewed_by != proposed_by),
    CHECK ((status = 'PROPOSED' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);

CREATE INDEX idx_icd10_synonym_term_trgm ON icd10_synonym USING gin (term gin_trgm_ops);
CREATE INDEX idx_icd10_synonym_code ON icd10_synonym (code, status) WHERE is_active;
CREATE INDEX idx_icd10_synonym_status ON icd10_synonym (status);

-- §5.4: "the mechanism that lets the reference layer improve itself" —
-- feeds the common-list suggestions (§4), identifies missing synonyms
-- (zero-result or never-selected searches), and drives the §5.2 "this
-- user's recent selections" ranking boost. selected_id is TEXT, not a
-- typed FK: it means an icd10_code today and will mean a service_item/item
-- id once those catalogues exist (§2.4), and which table it resolves
-- against depends on catalogue_type — a polymorphic reference, same
-- reasoning as vital_signs.context_ref_id (V10).
--
-- §5.4 "Retain 90 days, then aggregate" is an operational job, not built
-- here — nothing yet produces enough volume to need it, and adding a
-- purge job ahead of that is exactly the kind of premature machinery root
-- CLAUDE.md's working style warns against.
CREATE TABLE reference_search_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id   UUID NOT NULL REFERENCES app_user(id),
    department_id   UUID REFERENCES department(id),
    catalogue_type  TEXT NOT NULL CHECK (catalogue_type IN ('ICD10', 'SERVICE_ITEM', 'ITEM')),
    query_text      TEXT NOT NULL,
    result_count    INTEGER NOT NULL,
    selected_id     TEXT,
    searched_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reference_search_log_searched_at ON reference_search_log (searched_at);
CREATE INDEX idx_reference_search_log_actor ON reference_search_log (actor_user_id, catalogue_type, searched_at DESC);
CREATE INDEX idx_reference_search_log_selection ON reference_search_log (catalogue_type, selected_id)
    WHERE selected_id IS NOT NULL;
