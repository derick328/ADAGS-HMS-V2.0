-- Phase 5, pharmacy build brief item 3: dispensing. docs/spec/12-pharmacist.md
-- §4, §5, §7.
--
-- No orders/prescribing module exists yet anywhere in this codebase
-- (confirmed by grep — zero hits for "Prescription" before this
-- migration), so prescription_order/prescription_line live entirely
-- inside pharmacy, the same self-contained treatment lab_order/specimen
-- get inside laboratory (V21__lab_order_and_specimen.sql) rather than a
-- generic orders module. No substitution, doctor UI, or
-- substitution_allowed flag — those are explicitly later work.
--
-- The billing gate is per PrescriptionLine, not per PrescriptionOrder —
-- 12-pharmacist.md §5.1's isSatisfied(prescription_line) pseudocode is
-- literal here: one PrescriptionLine = one invoice line = one gate check,
-- the same "gating is per invoice line, never per invoice" structural
-- guarantee LabOrder gives lab charges (root CLAUDE.md "Billing").
--
-- item_id/invoice_line_id carry no DB FK — inventory and billing are
-- different modules (root CLAUDE.md "Modules": no cross-module foreign
-- keys except to patient and encounter) — same treatment as
-- lab_order.invoice_line_id and lab_result_reagent.stock_batch_id.

CREATE TABLE prescription_order (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id   UUID NOT NULL REFERENCES encounter(id),
    patient_id     UUID NOT NULL REFERENCES patient(id),
    facility_id    UUID NOT NULL REFERENCES facility(id),
    prescribed_by  UUID NOT NULL REFERENCES app_user(id),
    prescribed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_prescription_order_encounter ON prescription_order (encounter_id);

-- quantity_dispensed is materialised here, updated inside the same
-- transaction as every Dispense — root CLAUDE.md "Data" discipline applied
-- to a running total the same way invoice.outstanding_total_minor is
-- (V17__payment_and_cashier_shift.sql).
CREATE TABLE prescription_line (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_order_id  UUID NOT NULL REFERENCES prescription_order(id),
    item_id                UUID NOT NULL,
    quantity_prescribed    INTEGER NOT NULL CHECK (quantity_prescribed > 0),
    quantity_dispensed     INTEGER NOT NULL DEFAULT 0 CHECK (quantity_dispensed >= 0),
    dosage_instructions    TEXT NOT NULL,
    invoice_line_id        UUID NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'AWAITING_DISPENSE'
                               CHECK (status IN ('AWAITING_DISPENSE', 'PARTIALLY_DISPENSED', 'COMPLETED')),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- §4.4: "dispensed > prescribed -> rejected, hard block" — the database's half of that rule.
    CHECK (quantity_dispensed <= quantity_prescribed)
);

CREATE INDEX idx_prescription_line_order ON prescription_line (prescription_order_id);
CREATE INDEX idx_prescription_line_item ON prescription_line (item_id);

-- §5.4/§5.5: idempotency_key required — a double-tap on confirm must not
-- decrement stock twice or chart two dispenses (root CLAUDE.md
-- "Concurrency"). quantity_dispensed here is the total across this
-- dispense's DispenseLine rows (which may span several batches, §4.3).
CREATE TABLE dispense (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_line_id  UUID NOT NULL REFERENCES prescription_line(id),
    dispensed_by          UUID NOT NULL REFERENCES app_user(id),
    dispensed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    idempotency_key       TEXT NOT NULL UNIQUE,
    quantity_dispensed    INTEGER NOT NULL CHECK (quantity_dispensed > 0),
    counselling_notes     TEXT,
    dispensing_notes      TEXT
);

CREATE INDEX idx_dispense_prescription_line ON dispense (prescription_line_id);

-- §4.3: "Several lines per item, summing to quantity_dispensed" — 30
-- tablets may span two batches. unit_cost_amount_minor is copied from the
-- batch at dispense time (§4.3: "cost of goods for a past dispense must
-- not move when a later delivery arrives at a different price") — same
-- snapshot discipline root CLAUDE.md "Data" calls out for lab verification
-- levels and diagnosis titles. fefo_override_reason is required by the
-- application layer whenever the submitted batch selection deviates from
-- the FEFO suggestion, not by a CHECK here — the deviation is a
-- comparison against a query result, not a property of one row.
CREATE TABLE dispense_line (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispense_id              UUID NOT NULL REFERENCES dispense(id),
    item_id                  UUID NOT NULL,
    batch_id                 UUID NOT NULL,
    quantity                 INTEGER NOT NULL CHECK (quantity > 0),
    unit_cost_amount_minor   BIGINT NOT NULL CHECK (unit_cost_amount_minor >= 0),
    unit_cost_currency_code  TEXT NOT NULL DEFAULT 'TZS',
    fefo_override_reason     TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dispense_line_dispense ON dispense_line (dispense_id);
