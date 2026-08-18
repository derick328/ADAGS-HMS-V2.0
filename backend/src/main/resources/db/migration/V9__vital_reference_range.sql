-- Phase 2, clinical core, item 1: VitalReferenceRange.
-- 06-triage.md §2.2: age-banded, sex-aware bounds per vital, tunable by
-- clinical staff without a deploy — this is the first table in the
-- `clinical` module (backend/CLAUDE.md structure: "clinical/ notes,
-- diagnoses, allergies, vitals").
--
-- implausible_* bounds are ALWAYS both-sided when a band is configured
-- (every row in 06-triage.md §2.2's adult table has both). abnormal_*
-- bounds may legitimately be one-sided (diastolic_bp has no lower abnormal
-- bound; spo2 has no upper one) — that is a real clinical "no bound on this
-- side" state, not a gap. "Unconfigured" is therefore defined specifically
-- as implausible_low AND implausible_high both NULL — see
-- JdbcVitalReferenceRangeRepository / VitalReferenceRangeEvaluator.

CREATE TABLE vital_reference_range (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vital_type       TEXT NOT NULL CHECK (vital_type IN (
                         'TEMPERATURE', 'SYSTOLIC_BP', 'DIASTOLIC_BP', 'PULSE', 'RESPIRATORY_RATE', 'SPO2'
                     )),
    age_band         TEXT NOT NULL CHECK (age_band IN (
                         'NEONATE', 'INFANT', 'TODDLER', 'CHILD', 'ADOLESCENT', 'ADULT', 'ELDERLY'
                     )),
    -- Biological sex for physiological bounds, not patient.gender's identity
    -- enum. 'ANY' is the sex-agnostic default; every row seeded today is
    -- 'ANY' — the schema supports differentiation, nobody has supplied the
    -- numbers for it yet.
    sex              TEXT NOT NULL DEFAULT 'ANY' CHECK (sex IN ('MALE', 'FEMALE', 'ANY')),
    implausible_low  NUMERIC,
    implausible_high NUMERIC,
    abnormal_low     NUMERIC,
    abnormal_high    NUMERIC,
    effective_from   DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to     DATE,
    created_by       UUID NOT NULL REFERENCES app_user(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- >= not >: a same-day correction (a clinician retunes a band, then
    -- spots an error and retunes it again before end of day) is legitimate,
    -- not a data error to reject (see V15__service_item_and_price_list.sql
    -- for the same fix on price_list_entry, found via the same bug).
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- One current row per (vital, age band, sex) — never edited in place (root
-- CLAUDE.md "Billing" price-versioning convention, applied to reference
-- data generally: close effective_to, open a new row).
CREATE UNIQUE INDEX idx_vital_reference_range_current ON vital_reference_range (vital_type, age_band, sex)
    WHERE effective_to IS NULL;

-- Adult bounds — 06-triage.md §2.2, exact figures.
INSERT INTO vital_reference_range (vital_type, age_band, sex, implausible_low, implausible_high, abnormal_low, abnormal_high, created_by)
SELECT v.vital_type, 'ADULT', 'ANY', v.implausible_low, v.implausible_high, v.abnormal_low, v.abnormal_high,
       (SELECT id FROM app_user ORDER BY created_at ASC LIMIT 1)
FROM (VALUES
    ('TEMPERATURE',      30, 43,  35.5, 38.0),
    ('SYSTOLIC_BP',      40, 300, 90,   180),
    ('DIASTOLIC_BP',     20, 200, NULL, 110),
    ('PULSE',            20, 250, 50,   110),
    ('RESPIRATORY_RATE', 4,  80,  12,   20),
    ('SPO2',             50, 100, 94,   NULL)
) AS v(vital_type, implausible_low, implausible_high, abnormal_low, abnormal_high);

-- Paediatric bands named in 06-triage.md §2.2 ("neonate 0-1m, infant 1-12m,
-- toddler 1-3y, child 3-12y, adolescent 12-18y") — rows exist so the band
-- is a recognised, queryable value, but bounds are deliberately NULL. A
-- wrong paediatric vital-sign bound is a patient-safety failure, and this
-- spec does not supply the numbers. VitalReferenceRangeEvaluator refuses to
-- evaluate any vital against an unconfigured band rather than falling back
-- to adult bounds — see ClinicalStartupChecks for the startup warning that
-- lists exactly which (vital, band) pairs still need real numbers from a
-- clinician before any child can be triaged.
INSERT INTO vital_reference_range (vital_type, age_band, sex, created_by)
SELECT vital_type, age_band, 'ANY', (SELECT id FROM app_user ORDER BY created_at ASC LIMIT 1)
FROM (VALUES
    ('TEMPERATURE'), ('SYSTOLIC_BP'), ('DIASTOLIC_BP'), ('PULSE'), ('RESPIRATORY_RATE'), ('SPO2')
) AS vt(vital_type)
CROSS JOIN (VALUES
    ('NEONATE'), ('INFANT'), ('TODDLER'), ('CHILD'), ('ADOLESCENT')
) AS ab(age_band);
