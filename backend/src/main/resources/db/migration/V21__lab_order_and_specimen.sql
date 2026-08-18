-- Phase 4, diagnostics, item 2: LabOrder and Specimen with accession
-- numbers. docs/spec/15-lab-technician.md §4.1-§4.3.
--
-- LabOrder models a single ordered test, not a multi-test panel — a doctor
-- ordering three tests creates three LabOrder rows. This is what makes
-- root CLAUDE.md "Billing" ("Gating is per invoice line, never per
-- invoice") mechanical rather than something application code has to
-- enforce: one LabOrder -> one billing charge -> one invoice line -> one
-- Specimen -> one gate check. invoice_line_id has no DB FK (billing is a
-- different module; root CLAUDE.md "Modules": no cross-module foreign
-- keys except to patient and encounter) — same treatment as invoice_line's
-- own source_event_id.
--
-- §4.1/§4.2: accession number and quality assessment both happen at
-- "accept" — the same action, not two separate steps — so Specimen carries
-- both from creation. collected_by/received_by are modelled as separate
-- columns per the spec's schema even though this phase's single accept
-- action sets them to the same actor/timestamp (?Q5/?Q7 in the spec are
-- open: whether a distinct phlebotomy/collection step exists is future
-- work, not decided here).
CREATE TABLE lab_order (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id         UUID NOT NULL REFERENCES encounter(id),
    patient_id           UUID NOT NULL REFERENCES patient(id),
    facility_id          UUID NOT NULL REFERENCES facility(id),
    lab_test_id          UUID NOT NULL REFERENCES lab_test(id),
    ordered_by           UUID NOT NULL REFERENCES app_user(id),
    ordered_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    clinical_indication  TEXT,
    urgency              TEXT NOT NULL DEFAULT 'ROUTINE' CHECK (urgency IN ('STAT', 'URGENT', 'ROUTINE')),
    invoice_line_id      UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lab_order_encounter ON lab_order (encounter_id);

-- Multiple specimens per order are legitimate: §4.3 "recollection is not
-- re-charged" — a rejected specimen gets a second Specimen row against the
-- same lab_order_id, not a new order or a new charge.
CREATE TABLE specimen (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id      UUID NOT NULL REFERENCES lab_order(id),
    accession_no      TEXT NOT NULL UNIQUE,
    sample_type       TEXT NOT NULL,
    container_type    TEXT NOT NULL,
    collected_by      UUID NOT NULL REFERENCES app_user(id),
    collected_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    received_by       UUID REFERENCES app_user(id),
    received_at       TIMESTAMPTZ,
    quality           TEXT CHECK (quality IN
                          ('ADEQUATE', 'HAEMOLYSED', 'CLOTTED', 'INSUFFICIENT_VOLUME', 'WRONG_CONTAINER',
                           'MISLABELLED', 'DELAYED_TRANSPORT')),
    quality_note      TEXT,
    status            TEXT NOT NULL DEFAULT 'COLLECTED' CHECK (status IN ('COLLECTED', 'IN_PROGRESS', 'REJECTED')),
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- §4.2: quality_note required whenever quality != ADEQUATE.
    CHECK (quality = 'ADEQUATE' OR quality_note IS NOT NULL)
);

CREATE INDEX idx_specimen_lab_order ON specimen (lab_order_id);
