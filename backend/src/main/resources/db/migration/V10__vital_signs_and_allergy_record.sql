-- Phase 2, clinical core, item 2: VitalSigns and AllergyRecord storage.
-- 06-triage.md §2.2/§2.3/§2.4, 17-ward-nurse.md §4.1.
--
-- vital_signs is the ONE shared table for every context a set of vitals can
-- be taken in (TRIAGE, CONSULTATION, WARD_ROUNDS, POST_OP) — 17-ward-nurse.md
-- §4.1 names three separate vitals tables as a bug, because nobody could
-- then see a patient's trend across a visit. context_ref_id points at
-- whichever row owns the reading (a TriageAssessment, an admission, a
-- surgery) but is deliberately NOT a foreign key: TriageAssessment lives in
-- the encounters module, and backend/CLAUDE.md "Module boundary rules" #3
-- allows cross-module FKs only to patient and encounter. The pointer is
-- polymorphic anyway (different target tables per context), so an
-- application-level UUID is the correct shape here, not a workaround.
--
-- vital_reading normalises the six evaluated vitals (06-triage.md §2.2) into
-- one row per (vital_signs, vital_type) rather than eighteen wide columns
-- (value + applied_range_id + abnormal, times six) on vital_signs itself.
-- applied_range_id snapshots which VitalReferenceRange row the value was
-- actually checked against, so a later retune of that range (root
-- CLAUDE.md "Data": price-versioning applied to reference data generally)
-- never rewrites the clinical meaning of a historical reading.

CREATE TABLE vital_signs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id        UUID NOT NULL REFERENCES encounter(id),
    context             TEXT NOT NULL CHECK (context IN ('TRIAGE', 'CONSULTATION', 'WARD_ROUNDS', 'POST_OP')),
    context_ref_id      UUID NOT NULL,
    weight_kg           NUMERIC NOT NULL,
    height_cm           NUMERIC NOT NULL,
    -- 06-triage.md §2.3: "Computed only. Not stored as an editable field,
    -- not nurse-enterable." Display suppression under 18 is an API/frontend
    -- concern, not a storage one — out of scope here.
    bmi                 NUMERIC GENERATED ALWAYS AS (weight_kg / POWER(height_cm / 100.0, 2)) STORED,
    -- §7.3/§7.4: raised but not ruled on. Included nullable now per the
    -- item-2 instruction — adding these columns later means backfilling
    -- nulls across every existing reading instead of just leaving them null
    -- until the field set is confirmed.
    avpu                TEXT CHECK (avpu IN ('ALERT', 'VOICE', 'PAIN', 'UNRESPONSIVE')),
    blood_glucose_mmol  NUMERIC,
    recorded_by         UUID NOT NULL REFERENCES app_user(id),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- root CLAUDE.md "Time": occurred_at (when measured, editable within
    -- limits) vs recorded_at (system-set, immutable) are different fields.
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vital_signs_encounter ON vital_signs (encounter_id, occurred_at DESC);
CREATE INDEX idx_vital_signs_context ON vital_signs (context, context_ref_id);

CREATE TABLE vital_reading (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vital_signs_id    UUID NOT NULL REFERENCES vital_signs(id),
    vital_type        TEXT NOT NULL CHECK (vital_type IN (
                          'TEMPERATURE', 'SYSTOLIC_BP', 'DIASTOLIC_BP', 'PULSE', 'RESPIRATORY_RATE', 'SPO2'
                      )),
    value             NUMERIC NOT NULL,
    applied_range_id  UUID NOT NULL REFERENCES vital_reference_range(id),
    abnormal          BOOLEAN NOT NULL,
    UNIQUE (vital_signs_id, vital_type)
);

CREATE INDEX idx_vital_reading_vital_signs ON vital_reading (vital_signs_id);

-- 06-triage.md §2.4 / 07-doctor.md §6: "Allergies are written to the
-- patient, not only the encounter, so they persist to future visits."
-- Append-only for the clinical content (description/source/recorded_*) —
-- never an UPDATE or DELETE on those columns anywhere in this codebase, so
-- "what did we know and when" is always answerable. encounter_id is
-- nullable: a future allergy capture point (e.g. pharmacy) may not have a
-- live encounter to hang off. source values match 07-doctor.md §6's list —
-- only TRIAGE has a real writer today (V11); DOCTOR is this migration's
-- item 5, PHARMACIST/PATIENT_REPORTED are modelled ahead of their callers
-- the same way EncounterState models its full transition graph early.
CREATE TABLE allergy_record (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL REFERENCES patient(id),
    encounter_id        UUID REFERENCES encounter(id),
    description         TEXT NOT NULL,
    reaction            TEXT,
    severity            TEXT,
    source              TEXT NOT NULL CHECK (source IN ('TRIAGE', 'DOCTOR', 'PHARMACIST', 'PATIENT_REPORTED')),
    recorded_by         UUID NOT NULL REFERENCES app_user(id),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 07-doctor.md §6.1: "UNCONFIRMED and REFUTED are treated identically
    -- everywhere... the confirm/refute action is documentary, not
    -- functional." Updated in place on this same row — unlike the clinical
    -- content above, this is metadata ABOUT the record (who reviewed it,
    -- when, with what note), not a rewrite of what was observed, so it
    -- doesn't need a new row the way an amendment (supersedes_id, below) does.
    confirmation_status TEXT NOT NULL DEFAULT 'UNCONFIRMED'
                            CHECK (confirmation_status IN ('UNCONFIRMED', 'CONFIRMED', 'REFUTED')),
    confirmed_by        UUID REFERENCES app_user(id),
    confirmed_at        TIMESTAMPTZ,
    -- §6.1: "refuting requires a note."
    confirmation_note   TEXT,
    -- §6.2's [Amend] action: correcting the allergen text itself is a new
    -- row, never an edit — root CLAUDE.md "Data".
    supersedes_id       UUID REFERENCES allergy_record(id),
    CHECK ((confirmation_status = 'UNCONFIRMED' AND confirmed_by IS NULL AND confirmed_at IS NULL)
        OR (confirmation_status IN ('CONFIRMED', 'REFUTED') AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)),
    CHECK (confirmation_status <> 'REFUTED' OR confirmation_note IS NOT NULL)
);

CREATE INDEX idx_allergy_record_patient ON allergy_record (patient_id, recorded_at DESC);
