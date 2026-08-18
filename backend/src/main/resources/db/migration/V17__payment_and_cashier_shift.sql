-- Phase 3, billing gate, item 4: CashierShift, Payment, PaymentAllocation.
-- 02-billing-architecture.md §9, 03-billing-addendum.md §1.

CREATE TABLE cashier_shift (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id          UUID NOT NULL REFERENCES facility(id),
    opened_by            UUID NOT NULL REFERENCES app_user(id),
    opened_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    status               TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED', 'RECONCILED')),
    closed_by            UUID REFERENCES app_user(id),
    closed_at            TIMESTAMPTZ,
    -- Cashier's own declaration at close — not a reconciliation.
    counted_cash_minor   BIGINT CHECK (counted_cash_minor IS NULL OR counted_cash_minor >= 0),
    -- System-computed: SUM of settled CASH payments in this shift.
    expected_cash_minor  BIGINT,
    variance_minor       BIGINT,
    reconciled_by        UUID REFERENCES app_user(id),
    reconciled_at        TIMESTAMPTZ,
    currency_code        TEXT NOT NULL DEFAULT 'TZS',
    CHECK (status = 'OPEN'
        OR (closed_by IS NOT NULL AND closed_at IS NOT NULL AND counted_cash_minor IS NOT NULL
            AND expected_cash_minor IS NOT NULL AND variance_minor IS NOT NULL)),
    CHECK (status != 'RECONCILED' OR (reconciled_by IS NOT NULL AND reconciled_at IS NOT NULL)),
    -- "the cashier declares, they do not reconcile their own drawer" — the
    -- same second-person-constraint pattern as expenses/lab
    -- results/elevation requests (root CLAUDE.md "Authorisation").
    CHECK (reconciled_by IS NULL OR reconciled_by != closed_by)
);

-- A cashier cannot have two shifts open at once — the real guard behind
-- "every payment belongs to an open shift" resolving unambiguously.
CREATE UNIQUE INDEX idx_cashier_shift_one_open_per_cashier ON cashier_shift (opened_by) WHERE status = 'OPEN';

CREATE TABLE payment (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- patientId, not invoiceId: one payment's allocations can cover lines
    -- across more than one invoice (addendum §1 — the line picker ticks
    -- lines, not invoices).
    patient_id        UUID NOT NULL REFERENCES patient(id),
    cashier_shift_id  UUID NOT NULL REFERENCES cashier_shift(id),
    amount_minor      BIGINT NOT NULL CHECK (amount_minor > 0),
    currency_code     TEXT NOT NULL DEFAULT 'TZS',
    -- INSURANCE deliberately excluded — §9.2: coverage is a receivable,
    -- not cash received.
    method            TEXT NOT NULL CHECK (method IN (
                          'CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'MOBILE_MONEY', 'BANK_TRANSFER', 'CHEQUE'
                      )),
    reference         TEXT,
    status            TEXT NOT NULL CHECK (status IN ('SETTLED', 'PENDING_CLEARANCE')),
    -- UNIQUE, not just indexed: the database is the real guard against a
    -- double-tap recording two payments, not an application find-then-
    -- insert check (root CLAUDE.md "Concurrency").
    idempotency_key   TEXT NOT NULL UNIQUE,
    received_by       UUID NOT NULL REFERENCES app_user(id),
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Reference required for every non-cash method.
    CHECK (method = 'CASH' OR reference IS NOT NULL),
    -- CHEQUE lands PENDING_CLEARANCE, never SETTLED at creation; every
    -- other method settles immediately (no clearance step for them).
    CHECK ((method = 'CHEQUE' AND status = 'PENDING_CLEARANCE') OR (method != 'CHEQUE' AND status = 'SETTLED'))
);

CREATE INDEX idx_payment_cashier_shift ON payment (cashier_shift_id);
CREATE INDEX idx_payment_patient ON payment (patient_id);

CREATE TABLE payment_allocation (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id       UUID NOT NULL REFERENCES payment(id),
    invoice_line_id  UUID NOT NULL REFERENCES invoice_line(id),
    amount_minor     BIGINT NOT NULL CHECK (amount_minor > 0),
    currency_code    TEXT NOT NULL DEFAULT 'TZS',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_allocation_payment ON payment_allocation (payment_id);
CREATE INDEX idx_payment_allocation_invoice_line ON payment_allocation (invoice_line_id);

-- §9.3: "Materialised on Invoice, updated inside the same transaction as
-- any component change." credit_notes/write_offs/insurance_approved/
-- refunds are always 0 today — none of those mechanisms exist yet — so
-- outstanding is exactly gross_total_minor minus settled payments for
-- now; this is the column each of those, once built, plugs into.
ALTER TABLE invoice ADD COLUMN outstanding_total_minor BIGINT NOT NULL DEFAULT 0 CHECK (outstanding_total_minor >= 0);
