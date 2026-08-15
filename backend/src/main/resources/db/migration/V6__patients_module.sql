-- Phase 1, front office, item 1: the patients module.
-- docs/spec/05-receptionist.md §4, 05a-receptionist-decisions.md,
-- 01-architecture.md §5.2/§7, 21-multi-facility.md §3.
--
-- Patient, PatientIdentifier, NextOfKin, PatientAddress and InsurancePolicy
-- carry NO facility_id — 21-multi-facility.md §3: "people, and definitions
-- of things" are shared across branches. One human, one record, one
-- hospital number, valid everywhere. PatientRepository is therefore a
-- deliberate, documented exception to the facility-filter-by-default rule
-- (root CLAUDE.md "Facility", 21-multi-facility.md §4) — there is no
-- facility_id column for a base class to filter on, and there must never be
-- one added to these five tables without revisiting that decision first.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- insurance_scheme is a minimal stub — just enough for InsurancePolicy to
-- reference a managed list instead of free text (05-receptionist.md ⚠5).
-- The full insurance module (coverage rules, pre-auth, claims) is a later
-- phase; this table only carries what patient registration needs today.
CREATE TABLE insurance_scheme (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        TEXT NOT NULL UNIQUE,
    name        TEXT NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE patient (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_no           TEXT NOT NULL UNIQUE,
    given_name            TEXT NOT NULL,
    middle_name           TEXT,
    family_name           TEXT NOT NULL,
    date_of_birth         DATE NOT NULL,
    -- ⚠3: staff frequently only know an age, not a date. The application
    -- layer derives date_of_birth from age when no exact date is given and
    -- sets this true; every downstream display of age must check it.
    dob_is_estimated      BOOLEAN NOT NULL DEFAULT FALSE,
    gender                TEXT NOT NULL
                              CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    marital_status        TEXT
                              CHECK (marital_status IN ('SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED')),
    -- Optional and NOT globally required — see the partial unique index
    -- below. Format validation and irregular-ID override happen in the
    -- application layer (05-receptionist.md §4.2); no regex is enforced
    -- here because no spec seen so far gives one to enforce.
    national_id           TEXT,
    phone                 TEXT NOT NULL,
    email                 TEXT,
    -- ⚠ free-text per 05-receptionist.md §4.2; a lookup table is flagged
    -- there as a future improvement, not built now — out of scope for this
    -- slice.
    occupation            TEXT,
    -- ⚠4: the emergency fast-path (POST /patients/emergency) is not built
    -- in this slice — no encounter lifecycle yet — but the flag it needs is
    -- cheap to add now and expensive to retrofit onto existing rows later.
    identity_provisional  BOOLEAN NOT NULL DEFAULT FALSE,
    -- Admin-only, deceased/transferred-out. Never set by a front-desk
    -- action (⚠8) — dismissing a patient from today's list closes an
    -- encounter, not this flag. No encounter table exists yet to enforce
    -- that boundary in code; noted here so the invariant is not lost.
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    -- The merge pointer, present from the start (01-architecture.md §5.2:
    -- retrofitting this across fifteen tables later is brutal). Nothing is
    -- ever deleted on merge; every read follows this pointer to the
    -- surviving record. See fn_guard_no_merge_chain below for why this can
    -- only ever be a single hop.
    merged_into_id        UUID REFERENCES patient(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID REFERENCES app_user(id),
    CHECK (date_of_birth <= CURRENT_DATE),
    CHECK (date_of_birth >= CURRENT_DATE - INTERVAL '120 years'),
    CHECK (merged_into_id IS NULL OR merged_into_id != id)
);

-- Most patients will not have a national_id on file. A plain UNIQUE
-- constraint is the wrong tool here — on some Postgres configurations /
-- collations it would only tolerate a single NULL, and even where NULLs are
-- correctly treated as distinct, a plain unique index still spends space
-- indexing every NULL row for no purpose. WHERE national_id IS NOT NULL
-- both fixes the correctness question and keeps the index small.
CREATE UNIQUE INDEX idx_patient_national_id ON patient (national_id)
    WHERE national_id IS NOT NULL;

-- Duplicate detection (05-receptionist.md §4.1): trigram similarity over the
-- full name, GIN-indexed so a similarity query at registration is fast
-- against the whole table, not a sequential scan.
CREATE INDEX idx_patient_name_trgm ON patient
    USING GIN ((given_name || ' ' || family_name) gin_trgm_ops);

-- phone is deliberately NOT unique (families share numbers) but is matched
-- exactly during duplicate detection, so it still earns a plain index.
CREATE INDEX idx_patient_phone ON patient (phone);

CREATE INDEX idx_patient_merged_into ON patient (merged_into_id)
    WHERE merged_into_id IS NOT NULL;

-- A record that is itself a merge target (already pointed at by another
-- patient) must never become a redirect to a third record — that would make
-- "follow the pointer" a multi-hop walk with cycle potential instead of the
-- single hop every reader assumes. Un-merging (setting merged_into_id back
-- to NULL) stays allowed — 01-architecture.md §5.2 requires merge to be
-- reversible, not one-way.
CREATE FUNCTION fn_guard_no_merge_chain() RETURNS TRIGGER AS $$
DECLARE
    v_target_already_merged BOOLEAN;
BEGIN
    IF NEW.merged_into_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT (merged_into_id IS NOT NULL) INTO v_target_already_merged
      FROM patient WHERE id = NEW.merged_into_id;

    IF v_target_already_merged THEN
        RAISE EXCEPTION
            'Cannot merge patient % into % — % is itself already merged. Merge into the surviving record directly.',
            NEW.id, NEW.merged_into_id, NEW.merged_into_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_guard_no_merge_chain
    BEFORE UPDATE OF merged_into_id ON patient
    FOR EACH ROW
    WHEN (NEW.merged_into_id IS DISTINCT FROM OLD.merged_into_id)
    EXECUTE FUNCTION fn_guard_no_merge_chain();

-- 01-architecture.md §7: national ID, NHIF card, passport, other-facility
-- numbers. national_id itself stays a first-class Patient column (it drives
-- duplicate detection and has its own partial unique index above) — this
-- table is for the other, genuinely repeatable identifier types.
CREATE TABLE patient_identifier (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id        UUID NOT NULL REFERENCES patient(id),
    identifier_type   TEXT NOT NULL
                          CHECK (identifier_type IN ('NHIF_CARD', 'PASSPORT', 'OTHER_FACILITY_NUMBER', 'OTHER')),
    value             TEXT NOT NULL,
    issuing_authority TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (patient_id, identifier_type, value)
);

CREATE INDEX idx_patient_identifier_patient ON patient_identifier (patient_id);

-- All three fields required at registration (05-receptionist.md §4.2), but
-- modelled as repeatable — a patient may accumulate more than one contact
-- over time, and nothing here is ever overwritten in place.
CREATE TABLE next_of_kin (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL REFERENCES patient(id),
    full_name     TEXT NOT NULL,
    relationship  TEXT NOT NULL
                      CHECK (relationship IN ('SPOUSE', 'PARENT', 'CHILD', 'SIBLING', 'GUARDIAN', 'OTHER')),
    phone         TEXT NOT NULL,
    is_guardian   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_next_of_kin_patient ON next_of_kin (patient_id);

-- Repeatable, not flat columns on Patient (05-receptionist.md §4.2) —
-- people move, and old rows stay as history rather than being overwritten.
-- "Current address" is simply the newest CURRENT-type row for a patient.
CREATE TABLE patient_address (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id        UUID NOT NULL REFERENCES patient(id),
    address_type      TEXT NOT NULL CHECK (address_type IN ('CURRENT', 'PERMANENT')),
    -- ⚠ Tanzanian addressing does not fit street/city — region/district/ward
    -- plus a landmark, which is what staff actually use to find a home.
    region            TEXT,
    district          TEXT,
    ward              TEXT,
    street_or_village TEXT,
    landmark          TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patient_address_patient ON patient_address (patient_id, address_type, created_at DESC);

-- 05-receptionist.md ⚠5: an InsurancePolicy row, not three text columns —
-- "is this policy valid today", "who is the principal member", "has this
-- been verified" all need real fields, not string-parsing.
CREATE TABLE insurance_policy (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id           UUID NOT NULL REFERENCES patient(id),
    scheme_id            UUID NOT NULL REFERENCES insurance_scheme(id),
    policy_number        TEXT NOT NULL,
    member_number        TEXT,
    is_principal         BOOLEAN NOT NULL DEFAULT TRUE,
    principal_name       TEXT,
    valid_from           DATE,
    valid_to             DATE,
    verification_status  TEXT NOT NULL DEFAULT 'UNVERIFIED'
                             CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED', 'FAILED')),
    verified_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (is_principal OR principal_name IS NOT NULL),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from)
);

CREATE INDEX idx_insurance_policy_patient ON insurance_policy (patient_id);

-- Gapless per-facility-per-year numbering (root CLAUDE.md "Numbering"),
-- generalised over a sequence_name rather than built patient-specific —
-- invoice numbers, accession numbers and claim numbers all need the exact
-- same mechanism later. One row per (sequence, facility, year); allocation
-- is a single atomic UPSERT (see GaplessNumberAllocator), which serialises
-- concurrent callers on that row exactly the way a SELECT ... FOR UPDATE
-- would, without a separate lock statement.
CREATE TABLE sequence_counter (
    sequence_name TEXT NOT NULL,
    facility_id   UUID NOT NULL REFERENCES facility(id),
    year          INTEGER NOT NULL,
    last_value    BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (sequence_name, facility_id, year)
);
