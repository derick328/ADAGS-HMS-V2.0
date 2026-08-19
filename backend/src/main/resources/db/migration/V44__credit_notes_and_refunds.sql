-- Phase 7, item 1 (part 2): credit notes and refunds — the remaining two
-- components of the §9.3 outstanding-balance formula
-- (outstanding = total - payments_settled - credit_notes - write_offs
--  - insurance_approved + refunds). Write-offs landed in V43.

-- CreditNote: "Reduces what a patient owes without money moving" — a
-- service charged but not delivered, a wrong price, a goodwill
-- adjustment. Same second-signature-above-threshold shape as
-- invoice_write_off (V43), reusing that exact threshold setting per the
-- brief ("the same configurable threshold as write-offs") rather than a
-- second one. Unlike write-off, eligible against any live invoice, not
-- only a post-discharge receivable — a wrong price is discovered whenever
-- it's discovered, before or after the patient leaves. Never touches
-- invoice_line: a separate offsetting record, so invoice history and the
-- original charge stay intact and auditable.
CREATE TABLE credit_note (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id                  UUID NOT NULL REFERENCES invoice(id),
    amount_minor                BIGINT NOT NULL CHECK (amount_minor > 0),
    currency_code               TEXT NOT NULL DEFAULT 'TZS',
    reason                      TEXT NOT NULL,
    reference                   TEXT,
    issued_by                   UUID NOT NULL REFERENCES app_user(id),
    issued_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    requires_second_signature   BOOLEAN NOT NULL,
    status                      TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED')),
    approved_by                 UUID REFERENCES app_user(id),
    approved_at                 TIMESTAMPTZ,
    decision_note               TEXT,
    -- root CLAUDE.md "Authorisation": second-person constraint at the
    -- database. Only bites when a second signature is actually required —
    -- below the threshold the issuer is deliberately self-authorized.
    CHECK (NOT requires_second_signature OR approved_by IS NULL OR approved_by != issued_by),
    CHECK ((status = 'PENDING' AND approved_by IS NULL AND approved_at IS NULL)
        OR (status IN ('APPLIED', 'REJECTED') AND approved_by IS NOT NULL AND approved_at IS NOT NULL))
);

CREATE INDEX idx_credit_note_invoice ON credit_note (invoice_id);
CREATE INDEX idx_credit_note_status ON credit_note (status);

-- Refund: money going back out, against a specific settled payment
-- ALLOCATION — not the payment as a whole (one payment's allocations can
-- cover lines across more than one invoice, addendum §1) and not the
-- invoice directly, so every refund traces to the exact line/invoice it
-- reverses without ambiguity. refunded_minor is the materialized running
-- total per allocation — same "guard in the UPDATE's WHERE clause, not a
-- prior read" concurrency discipline as invoice.outstanding_total_minor —
-- so two refunds racing against the same allocation can't together
-- exceed what was actually paid on it.
ALTER TABLE payment_allocation
    ADD COLUMN refunded_minor BIGINT NOT NULL DEFAULT 0 CHECK (refunded_minor >= 0);
ALTER TABLE payment_allocation
    ADD CONSTRAINT chk_payment_allocation_refund_not_over_amount CHECK (refunded_minor <= amount_minor);

CREATE TABLE refund (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_allocation_id       UUID NOT NULL REFERENCES payment_allocation(id),
    amount_minor                BIGINT NOT NULL CHECK (amount_minor > 0),
    currency_code               TEXT NOT NULL DEFAULT 'TZS',
    reason                      TEXT NOT NULL,
    requested_by                UUID NOT NULL REFERENCES app_user(id),
    requested_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    requires_second_signature   BOOLEAN NOT NULL,
    status                      TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED')),
    decided_by                  UUID REFERENCES app_user(id),
    decided_at                  TIMESTAMPTZ,
    decision_note               TEXT,
    CHECK (NOT requires_second_signature OR decided_by IS NULL OR decided_by != requested_by),
    CHECK ((status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status IN ('APPLIED', 'REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL))
);

CREATE INDEX idx_refund_allocation ON refund (payment_allocation_id);
CREATE INDEX idx_refund_status ON refund (status);

-- New authority: nothing in the V5 catalogue covers issuing a credit
-- note. Refunds reuse PAYMENT_REFUND (V5), already ACCOUNTANT's and never
-- RECEPTIONIST's — "never a receptionist action" already holds without a
-- new permission.
INSERT INTO permission (code, module, description, is_clinical)
VALUES ('INVOICE_CREDIT_NOTE_ISSUE', 'billing', 'Issue a credit note against an invoice', FALSE);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
 WHERE r.code = 'ACCOUNTANT' AND p.code = 'INVOICE_CREDIT_NOTE_ISSUE'
ON CONFLICT DO NOTHING;
