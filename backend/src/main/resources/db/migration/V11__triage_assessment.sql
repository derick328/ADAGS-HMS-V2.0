-- Phase 2, clinical core, item 2: TriageAssessment storage.
-- 06-triage.md §2. Lives in the encounters module (backend/CLAUDE.md:
-- "encounters/ visit lifecycle, queues, triage"), not clinical — VitalSigns
-- (V10) is the shared clinical-data table; TriageAssessment is a single
-- module-owned aggregate of the triage-specific fields around it.
--
-- No column here references vital_signs — that link is the other
-- direction (vital_signs.context_ref_id -> this table's id) and is
-- deliberately not a foreign key either, for the same cross-module-FK
-- reason documented in V10.
--
-- supersedes_id is a same-table self-reference (an in-module FK, allowed):
-- 06-triage.md §1 "Corrections create an amendment (supersedes_id), never
-- an in-place edit" — the same amends_id pattern root CLAUDE.md "Data"
-- requires of clinical records generally.
--
-- The conditional-field CHECK constraints below mirror
-- TriageAssessment.create()'s validation exactly (root CLAUDE.md
-- "Concurrency": a real constraint, not only an application-layer check —
-- the same reasoning applied here to data integrity rather than a race).

CREATE TABLE triage_assessment (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id                  UUID NOT NULL REFERENCES encounter(id),
    chief_complaint               TEXT NOT NULL CHECK (char_length(chief_complaint) BETWEEN 3 AND 500),
    current_symptoms              TEXT NOT NULL,
    -- Snapshot at the moment of triage — the append-only AllergyRecord
    -- (V10, clinical module) is the persistent, cross-visit history this
    -- text is written into as part of §6's submission transaction.
    allergies                     TEXT NOT NULL,
    comorbidities                 TEXT NOT NULL,
    mode_of_ambulation            TEXT NOT NULL CHECK (mode_of_ambulation IN (
                                      'WALKING', 'INFANT_IN_MOTHERS_LAP', 'WHEELCHAIR_ASSISTED', 'STRETCHER_ASSISTED'
                                  )),
    has_pain                      BOOLEAN NOT NULL,
    pain_score                    SMALLINT CHECK (pain_score BETWEEN 0 AND 10),
    pain_location                 TEXT,
    fall_risk                     BOOLEAN NOT NULL,
    fall_score                    SMALLINT,
    infection_risk                BOOLEAN NOT NULL,
    -- Nullable: only present for patients within the pregnancy-prompt age
    -- bounds seeded below (§2.7 "Auto-appears when gender = FEMALE and age
    -- is 12-50... Age bounds are configuration, not constants").
    pregnancy_status               TEXT CHECK (pregnancy_status IN ('YES', 'NO', 'UNKNOWN')),
    gestational_weeks             SMALLINT,
    priority                      TEXT NOT NULL CHECK (priority IN ('IMMEDIATE', 'URGENT', 'LESS_URGENT', 'NON_URGENT')),
    immediate_reason              TEXT CHECK (immediate_reason IN (
                                      'THREATENED_OR_OBSTRUCTED_AIRWAY', 'SEVERE_RESPIRATORY_DISTRESS',
                                      'SHOCK_OR_CIRCULATORY_COLLAPSE', 'UNRESPONSIVE_OR_RESPONDING_TO_PAIN_ONLY',
                                      'ACTIVE_UNCONTROLLED_BLEEDING', 'ACTIVE_CONVULSION', 'OBSTETRIC_EMERGENCY', 'OTHER'
                                  )),
    immediate_reason_other_text   TEXT,
    notes                         TEXT CHECK (notes IS NULL OR char_length(notes) <= 2000),
    supersedes_id                 UUID REFERENCES triage_assessment(id),
    assessed_by                   UUID NOT NULL REFERENCES app_user(id),
    assessed_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- §2.5: hidden/discarded when false, required when true.
    CHECK ((has_pain AND pain_score IS NOT NULL AND pain_location IS NOT NULL)
        OR (NOT has_pain AND pain_score IS NULL AND pain_location IS NULL)),
    -- §2.6: same rule for fall risk.
    CHECK ((fall_risk AND fall_score IS NOT NULL) OR (NOT fall_risk AND fall_score IS NULL)),
    -- gestational_weeks only makes sense once pregnancy_status has been asked at all.
    CHECK (pregnancy_status IS NOT NULL OR gestational_weeks IS NULL),
    -- §4.1: IMMEDIATE always carries a reason; every other priority carries none.
    CHECK (priority = 'IMMEDIATE' OR immediate_reason IS NULL),
    CHECK (priority <> 'IMMEDIATE' OR immediate_reason IS NOT NULL),
    -- §4.1: "Other -> free text required".
    CHECK (immediate_reason = 'OTHER' OR immediate_reason_other_text IS NULL),
    CHECK (immediate_reason <> 'OTHER' OR immediate_reason_other_text IS NOT NULL)
);

CREATE INDEX idx_triage_assessment_encounter ON triage_assessment (encounter_id, assessed_at DESC);
CREATE INDEX idx_triage_assessment_supersedes ON triage_assessment (supersedes_id) WHERE supersedes_id IS NOT NULL;

-- §2.7: "Age bounds are configuration, not constants" — same
-- ProtocolSettingReader mechanism V7 introduced for the duplicate-detection
-- thresholds, reused here rather than a hardcoded 12/50 in Java.
INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'triage.pregnancy_prompt.min_age_years', '12'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;

INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'triage.pregnancy_prompt.max_age_years', '50'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;
