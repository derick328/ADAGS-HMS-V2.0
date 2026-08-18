-- Phase 4, diagnostics, item 1: LabTest catalogue with parameters and
-- reference ranges. docs/spec/15-lab-technician.md §5.1,
-- 16-lab-verification-model.md v0.2 §2/§9, root CLAUDE.md "Safety"/"Data".
--
-- The result form is GENERATED from lab_test_parameter — the technician
-- enters values only, never the parameter name, unit, or range (§5.1: "Hb /
-- HB / Haemoglobin are four parameters, no doctor can trend a patient's
-- haemoglobin"). That generation is phase 5's work (result entry); this
-- migration only lays the catalogue tables it will read from.
--
-- verification_level/produces_machine_output follow 16-lab-verification-model.md
-- v0.2 §9 exactly (NONE default — "with every test at NONE... behaviour is
-- identical to the current flow: enter, release, done") rather than v0.1's
-- is_sensitive boolean, since v0.2 supersedes v0.1.
--
-- No seed data: "Same rule as VitalReferenceRange: seed nothing you cannot
-- cite. Structure now, values by the clinical lead" (Phase 4 build brief).
-- A LabTest's code is expected to match a service_item.code the same way
-- CONSULT_GENERAL does for consultations — an admin adding a test to the
-- catalogue also adds its priced service_item, no DB FK between them
-- (cross-module; root CLAUDE.md "Modules": no cross-module foreign keys
-- except to patient and encounter).

CREATE TABLE lab_test (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                     TEXT NOT NULL UNIQUE,
    name                     TEXT NOT NULL,
    specimen_type            TEXT NOT NULL,
    container_type           TEXT NOT NULL,
    expected_tat_hours       INT NOT NULL CHECK (expected_tat_hours > 0),
    verification_level       TEXT NOT NULL DEFAULT 'NONE'
                                 CHECK (verification_level IN ('NONE', 'SELF', 'SECOND_PERSON', 'SPECIALIST')),
    produces_machine_output  BOOLEAN NOT NULL DEFAULT TRUE,
    is_active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE lab_test_parameter (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id     UUID NOT NULL REFERENCES lab_test(id),
    code        TEXT NOT NULL,
    name        TEXT NOT NULL,
    unit        TEXT,
    sort_order  INT NOT NULL DEFAULT 0,
    method      TEXT,
    loinc_code  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (test_id, code)
);

CREATE INDEX idx_lab_test_parameter_test ON lab_test_parameter (test_id);

-- Age in months (not a banded enum like vital_reference_range) per
-- §5.1's exact field list. sex 'ANY' is the sex-agnostic default, same
-- vocabulary as vital_reference_range.
--
-- implausible_low/implausible_high extend §5.1's four given bounds: §5.3
-- ("two bounds per parameter, same as vitals: implausible rejects as a
-- typo, abnormal flags and saves — a potassium of 70 is a decimal error,
-- not a finding") needs a tier strictly outside critical_low/critical_high
-- to reject a typo — critical itself must never be rejected, a critical
-- value is real and must alert (§7), not bounce as invalid input. Same
-- both-non-null-together convention as vital_reference_range.
-- normal/critical stay independently nullable — "no bound on this side"
-- is a real state (e.g. a parameter with no acute-danger upper tier), same
-- reasoning as vital_reference_range's abnormal_low/abnormal_high.
CREATE TABLE lab_reference_range (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parameter_id      UUID NOT NULL REFERENCES lab_test_parameter(id),
    age_min_months    INT NOT NULL DEFAULT 0 CHECK (age_min_months >= 0),
    age_max_months    INT CHECK (age_max_months IS NULL OR age_max_months >= age_min_months),
    sex               TEXT NOT NULL DEFAULT 'ANY' CHECK (sex IN ('MALE', 'FEMALE', 'ANY')),
    implausible_low   NUMERIC,
    implausible_high  NUMERIC,
    normal_low        NUMERIC,
    normal_high       NUMERIC,
    critical_low      NUMERIC,
    critical_high     NUMERIC,
    effective_from    DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to      DATE,
    created_by        UUID NOT NULL REFERENCES app_user(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- >= not >: a same-day correction is legitimate (V15, V9).
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- One current row per (parameter, age band start, sex) — never edited in
-- place, same versioning as price_list_entry / vital_reference_range.
CREATE UNIQUE INDEX idx_lab_reference_range_current
    ON lab_reference_range (parameter_id, age_min_months, sex)
    WHERE effective_to IS NULL;
