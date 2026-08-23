-- Phase 8, surgery module, item 1: SurgeryOrder and theatre scheduling.
-- docs/spec/04-procedure-bedday-rules.md, root CLAUDE.md "Billing" /
-- "Concurrency". New module ("surgery") not yet in backend/CLAUDE.md's
-- structure list — that list has been stale since Phase 2 (it never
-- gained "finance" either when that module shipped in Phase 7), so this
-- follows the pattern already established rather than treating the list
-- as exhaustive.

-- (1) permission.module CHECK (V5) needs a new value for the module's own
-- permission rows below.
ALTER TABLE permission DROP CONSTRAINT permission_module_check;
ALTER TABLE permission ADD CONSTRAINT permission_module_check
    CHECK (module IN (
        'admin', 'audit', 'billing', 'clinical', 'encounters', 'finance',
        'identity', 'inpatient', 'insurance', 'inventory', 'laboratory',
        'orders', 'patients', 'pharmacy', 'reference', 'scheduling', 'surgery'
    ));

-- (2) THEATRE_MANAGE — same admin-defines-the-physical-resource bundle as
-- WARD_BED_MANAGE/STORE_MANAGE (V5). SURGERY_ORDER_CREATE and
-- SURGERY_EPISODE_DOCUMENT already exist (V5, granted to SPECIALIST_DOCTOR
-- and WARD_NURSE respectively) — this module reuses them rather than
-- minting duplicate codes; fn_grant_permissions was dropped at the end of
-- V5, so granting here follows V28's direct-INSERT idiom.
INSERT INTO permission (code, module, description, is_clinical)
VALUES ('THEATRE_MANAGE', 'admin', 'Define theatres', FALSE);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
 WHERE r.code = 'ADMIN' AND p.code = 'THEATRE_MANAGE'
ON CONFLICT DO NOTHING;

-- (3) IMMEDIATE surgery urgency bypasses the billing gate for the whole
-- encounter via the existing PaymentBypass mechanism (root CLAUDE.md
-- "Billing": "An active PaymentBypass satisfies the gate... IMMEDIATE
-- triage priority creates one automatically" — this is the surgery-side
-- analogue). Widens V12's source CHECK; a migration is immutable once
-- merged, so this is a new ALTER, never an edit to V12 itself.
ALTER TABLE payment_bypass DROP CONSTRAINT payment_bypass_source_check;
ALTER TABLE payment_bypass ADD CONSTRAINT payment_bypass_source_check
    CHECK (source IN ('TRIAGE_IMMEDIATE', 'SURGERY_IMMEDIATE'));

-- (4) Theatre — deliberately minimal (root CLAUDE.md "Facility": a place,
-- so it carries facility_id). active is the soft-deactivation flag every
-- other admin-defined resource (Ward, Item) carries instead of a hard
-- delete.
CREATE TABLE theatre (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id  UUID NOT NULL REFERENCES facility(id),
    name         TEXT NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- (5) SurgeryOrder.
--
-- procedure_service_item_code/procedure_name mirror the Diagnosis
-- icd10_code/icd10_title_snapshot split (07-doctor.md §4.1): the code is
-- what BillingFacade.postServiceCharge resolves a price against (root
-- CLAUDE.md "Billing": "Nobody types a price... Prices resolve from
-- PriceListEntry"), procedure_name is the catalogue name snapshotted at
-- scheduling time so a later service_item rename doesn't rewrite history.
-- No service_item_id/invoice_line_id FK — billing is a different module
-- (root CLAUDE.md "Modules": no cross-module foreign keys except to
-- patient and encounter) — same treatment as lab_order.invoice_line_id
-- (V21).
--
-- time_slot is a free but required label, not a fixed enum: nothing in
-- the spec defines theatre scheduling granularity, and the partial unique
-- index below only needs slot values to be equality-comparable, not a
-- particular vocabulary — that's an admin/UI concern this migration
-- doesn't need to guess at.
--
-- consent_obtained/consent_document_key carry no CHECK requiring TRUE:
-- consent can be revoked after scheduling ("Check again at procedure
-- start — consent may be revoked"), so the requirement that scheduling
-- itself needs a signed document is enforced once, at INSERT, by
-- ScheduleSurgeryService/SurgeryOrder.schedule — not a standing constraint
-- that would then block a legitimate revocation.
--
-- version: optimistic locking, same convention as Encounter/Invoice/Order/
-- Admission (backend/CLAUDE.md "Rules").
CREATE TABLE surgery_order (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id                  UUID NOT NULL REFERENCES encounter(id),
    procedure_service_item_code   TEXT NOT NULL,
    procedure_name                TEXT NOT NULL,
    surgery_type                  TEXT NOT NULL,
    urgency                       TEXT NOT NULL CHECK (urgency IN ('ELECTIVE', 'URGENT', 'IMMEDIATE')),
    anesthesia_type               TEXT NOT NULL,
    scheduled_date                DATE NOT NULL,
    time_slot                     TEXT NOT NULL,
    theatre_id                    UUID NOT NULL REFERENCES theatre(id),
    estimated_duration_minutes    INTEGER NOT NULL CHECK (estimated_duration_minutes > 0),
    assigned_nurses                JSONB NOT NULL DEFAULT '[]'::jsonb,
    pre_op_notes                   TEXT,
    consent_obtained               BOOLEAN NOT NULL DEFAULT FALSE,
    consent_document_key          TEXT,
    status                        TEXT NOT NULL DEFAULT 'SCHEDULED'
                                       CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    invoice_line_id               UUID NOT NULL,
    scheduled_by                  UUID NOT NULL REFERENCES app_user(id),
    scheduled_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                       INTEGER NOT NULL DEFAULT 0
);

-- root CLAUDE.md "Concurrency": "a read-then-write check loses the race" —
-- this is the whole protection against two procedures in one theatre slot.
-- Excludes CANCELLED so a cancelled booking frees its slot for reuse.
CREATE UNIQUE INDEX idx_theatre_slot_not_cancelled
    ON surgery_order (theatre_id, scheduled_date, time_slot)
    WHERE status != 'CANCELLED';

CREATE INDEX idx_surgery_order_encounter ON surgery_order (encounter_id);
