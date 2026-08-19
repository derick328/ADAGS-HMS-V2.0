-- Phase 6, item 4: ward care execution, MAR. 17-ward-nurse.md §5.1-§5.4.
--
-- "The most important change in this document": the schedule is generated
-- from the prescription, never typed. Rows are pre-created at
-- scheduled_at with status DUE; a missed dose is a visible unmarked slot
-- past its due time, not an absent row (§5.1's whole point — a typed MAR
-- shows only what was given and looks complete when a dose is forgotten).
--
-- No medication_name/route/dose columns here on purpose (§5.1: "dropped as
-- stored fields... duplicating invites divergence") — those are read live
-- from prescription_line via PharmacyFacade whenever the MAR is rendered.
-- prescription_line_id carries no DB FK: pharmacy and inpatient are
-- different modules (root CLAUDE.md "Modules" — no cross-module foreign
-- keys except to patient and encounter), same treatment prescription_line's
-- own item_id already gets.
--
-- batch_id: per your instruction, the batch comes from the Dispense record
-- already made when pharmacy dispensed this line — administering links to
-- it, it does not decrement stock a second time (stock left the ledger at
-- dispense). No DB FK for the same cross-module reason as prescription_line_id.
CREATE TABLE medication_administration (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id           UUID NOT NULL REFERENCES admission(id),
    prescription_line_id   UUID NOT NULL,
    scheduled_at           TIMESTAMPTZ NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'DUE'
                               CHECK (status IN ('DUE', 'GIVEN', 'REFUSED', 'OMITTED', 'HELD')),
    administered_at        TIMESTAMPTZ,
    administered_by        UUID REFERENCES app_user(id),
    dose_given             TEXT,
    batch_id               UUID,
    -- §5.3: two-person check for Item.requires_administration_witness
    -- medicines. Permission-gated (MEDICATION_WITNESS) at the application
    -- layer; this CHECK is the database's half — the same second-person
    -- pattern root CLAUDE.md "Authorisation" requires on expenses and lab
    -- verification.
    witness_user_id        UUID REFERENCES app_user(id),
    omission_reason        TEXT CHECK (omission_reason IN (
                               'REFUSED_BY_PATIENT', 'NIL_BY_MOUTH', 'PATIENT_ABSENT', 'DRUG_UNAVAILABLE',
                               'CLINICAL_HOLD', 'VOMITED', 'IV_ACCESS_LOST', 'DUPLICATE_DOSE'
                           )),
    omission_notes         TEXT,
    remarks                TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (witness_user_id IS NULL OR witness_user_id != administered_by),
    CHECK (status = 'DUE' OR administered_at IS NOT NULL),
    -- REFUSED/OMITTED/HELD all mean "not given" and need a reason;
    -- DUPLICATE_DOSE etc. only make clinical sense for those three.
    CHECK (status NOT IN ('REFUSED', 'OMITTED', 'HELD') OR omission_reason IS NOT NULL)
);

-- Generation must not double-post the same slot if called twice for the
-- same prescription line (e.g. a retried request) — the actual guard, not
-- an application-level pre-check (root CLAUDE.md "Concurrency").
CREATE UNIQUE INDEX idx_medication_administration_line_slot
    ON medication_administration (prescription_line_id, scheduled_at);

-- What the "overdue MAR" view (17-ward-nurse.md §11) and the ward dashboard
-- scan: DUE slots for an admission, ordered by when they fell due.
CREATE INDEX idx_medication_administration_admission_status
    ON medication_administration (admission_id, status, scheduled_at);
