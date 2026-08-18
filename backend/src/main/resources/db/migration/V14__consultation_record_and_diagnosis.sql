-- Phase 2, clinical core, item 5 (closes Phase 2): the consultation module.
-- docs/spec/07-doctor.md §4-§5, §10; 09-doctor-incomplete-consultations.md.

-- §10: "finalize = false -> status = OPEN (editable); finalize = true ->
-- status = FINALIZED (immutable). Corrections create an amendment... nobody
-- un-finalizes." OPEN is the one mutable phase — autosave (item 5's #4)
-- updates this same row every 20s; once FINALIZED, every further column
-- here is read-only and a correction is a new row via amends_id.
--
-- author_id, not "doctor_id": 07-doctor.md §7.1 has a receiving doctor
-- create their OWN ConsultationRecord on internal referral, alongside the
-- referring doctor's already-finalized one — this table holds one row per
-- author per encounter (per amendment chain), not one row per encounter.
CREATE TABLE consultation_record (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id                    UUID NOT NULL REFERENCES encounter(id),
    author_id                       UUID NOT NULL REFERENCES app_user(id),
    status                          TEXT NOT NULL DEFAULT 'OPEN'
                                        CHECK (status IN ('OPEN', 'FINALIZED', 'ABANDONED')),
    -- §5.3 "the doctor's own work". chief_complaint is deliberately absent —
    -- triage already recorded the patient's own words (06-triage.md §2.1);
    -- history_of_presenting_illness is the clinical expansion of it, not a
    -- restatement.
    history_of_presenting_illness   TEXT,
    past_medical_history            TEXT,
    past_surgical_history           TEXT,
    family_social_history           TEXT,
    physical_examination            TEXT,
    clinical_notes                  TEXT,
    treatment_plan                  TEXT,
    -- 09-doc §5.2: completing a note from the incomplete-consultations list,
    -- hours or days after the patient was seen, needs its own marker and a
    -- note explaining what the entry is based on — "a different evidential
    -- object from one written at the bedside."
    retrospective_completion        BOOLEAN NOT NULL DEFAULT FALSE,
    retrospective_note              TEXT,
    completed_at                    TIMESTAMPTZ,
    abandoned_reason                TEXT,
    abandoned_at                    TIMESTAMPTZ,
    abandoned_by                    UUID REFERENCES app_user(id),
    -- §10: amendment chain for a correction to an already-FINALIZED record.
    amends_id                       UUID REFERENCES consultation_record(id),
    amendment_reason                TEXT,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (NOT retrospective_completion
        OR (retrospective_note IS NOT NULL AND char_length(retrospective_note) >= 20)),
    CHECK ((status = 'FINALIZED') = (completed_at IS NOT NULL)),
    CHECK ((status = 'ABANDONED') = (abandoned_at IS NOT NULL AND abandoned_by IS NOT NULL AND abandoned_reason IS NOT NULL)),
    CHECK (amends_id IS NULL OR amendment_reason IS NOT NULL)
);

-- item 4's autosave key (encounter_id, author_id): at most one OPEN record
-- per author per encounter at a time. A FINALIZED/ABANDONED row doesn't
-- occupy this slot — a fresh amendment or (for internal referral) a
-- different author's own note can still open one.
CREATE UNIQUE INDEX idx_consultation_record_open_author ON consultation_record (encounter_id, author_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_consultation_record_encounter ON consultation_record (encounter_id, created_at DESC);

-- item 5's incomplete-consultation query: status/author/created_at is
-- exactly its WHERE clause, encounter.state is joined in at query time
-- (JdbcConsultationRecordRepository) rather than duplicated onto this row.
CREATE INDEX idx_consultation_record_incomplete ON consultation_record (author_id, created_at)
    WHERE status = 'OPEN';

-- §4.1. icd10_code is plain TEXT, not a foreign key to referencedata's
-- icd10_code table: clinical and referencedata are different modules, and
-- backend/CLAUDE.md "Module boundary rules" #3 allows cross-module FKs only
-- to patient and encounter. icd10_title_snapshot is exactly the mechanism
-- that makes this safe rather than a workaround — "ICD-10 titles are
-- revised between releases, and a record written in 2026 must still read
-- as it did in 2026" means this row was never meant to always resolve
-- through the live reference table anyway.
CREATE TABLE diagnosis (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id           UUID NOT NULL REFERENCES encounter(id),
    consultation_record_id UUID NOT NULL REFERENCES consultation_record(id),
    icd10_code             TEXT NOT NULL,
    icd10_title_snapshot   TEXT NOT NULL,
    free_text_clarifier    TEXT,
    certainty              TEXT NOT NULL CHECK (certainty IN ('PROVISIONAL', 'CONFIRMED', 'RULED_OUT')),
    is_primary             BOOLEAN NOT NULL DEFAULT FALSE,
    diagnosed_by           UUID NOT NULL REFERENCES app_user(id),
    diagnosed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    amends_id              UUID REFERENCES diagnosis(id),
    amendment_reason       TEXT,
    CHECK (amends_id IS NULL OR amendment_reason IS NOT NULL)
);

-- §4.1: "Exactly one may be primary" — UNIQUE (encounter_id) WHERE is_primary.
CREATE UNIQUE INDEX idx_diagnosis_one_primary ON diagnosis (encounter_id) WHERE is_primary;

CREATE INDEX idx_diagnosis_encounter ON diagnosis (encounter_id, diagnosed_at DESC);
CREATE INDEX idx_diagnosis_consultation_record ON diagnosis (consultation_record_id);

-- item 5: "Thresholds from protocol_setting, not constants."
-- 09-doc §9 lists escalate_to_lead_hours/auto_close_days alongside this key
-- too, but only the incomplete-consultation prompt threshold has a reader
-- in this phase (IncompleteConsultationService) — seeding the other two now
-- would be configuration with no code path to apply it.
INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'consultation.incomplete_prompt_hours', '12'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;
