-- 06-triage.md §1/§3: "Assessments autosave server-side every 20 seconds
-- keyed to (encounter_id, author_id), so a nurse whose device fails resumes
-- on another device with the draft intact." backend/CLAUDE.md already names
-- this exact key under "Autosave endpoints (consultation drafts, triage
-- assessments)".
--
-- This is scratch working state, not the clinical record — the immutable,
-- amend-only row is triage_assessment (V11), written once by
-- SubmitTriageAssessmentService on final submit. A draft is freely
-- overwritten every autosave, which is why the primary key is the
-- composite (encounter_id, author_id) rather than a surrogate id: an
-- upsert by that exact pair is the whole point, and §3's "no locking...
-- a second nurse CAN open the same patient" means more than one author can
-- hold a live draft for the same encounter at once.
--
-- form_state is intentionally opaque JSONB rather than a column per field:
-- it exists purely to resume an in-progress form, never queried by shape.
-- priority and mode_of_ambulation are pulled out as real columns because
-- two things outside the resuming nurse's own browser need to read them
-- without parsing JSON: the queue's live re-sort ("Re-sorts live... on any
-- priority change", §3) and the queue row's arrival-mode display (§1) once
-- an assessment is under way.
CREATE TABLE triage_assessment_draft (
    encounter_id       UUID NOT NULL REFERENCES encounter(id),
    author_id          UUID NOT NULL REFERENCES app_user(id),
    priority           TEXT CHECK (priority IN ('IMMEDIATE', 'URGENT', 'LESS_URGENT', 'NON_URGENT')),
    mode_of_ambulation TEXT CHECK (mode_of_ambulation IN (
                           'WALKING', 'INFANT_IN_MOTHERS_LAP', 'WHEELCHAIR_ASSISTED', 'STRETCHER_ASSISTED'
                       )),
    form_state         JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (encounter_id, author_id)
);

-- The queue's "any nurse currently assessing this encounter" projection —
-- most-recently-updated draft per encounter, across every author.
CREATE INDEX idx_triage_assessment_draft_encounter ON triage_assessment_draft (encounter_id, updated_at DESC);
