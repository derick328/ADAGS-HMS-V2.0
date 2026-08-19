-- Phase 7, item 4 (part 1): expenses. 18-admin.md §5 (approval
-- separation), §8 (fields, delete-on-expenses, recurring idempotency).
--
-- CHECK (created_by IS DISTINCT FROM approved_by) — root CLAUDE.md
-- "Authorisation": "the constraint, not a policy. One person recording,
-- approving, and paying is the standard embezzlement path." IS DISTINCT
-- FROM (not !=) so the check passes while approved_by is still NULL
-- (unapproved), same idiom as V2's requested_by/reviewed_by pair.
--
-- recurring/frequency/template_id/period live on this same table rather
-- than a separate expense_template — the literal column list this item
-- gives has no such table, and a recurring=TRUE row IS the template: its
-- own data (title, category, amount, payment_method, paid_to) is what
-- each period's occurrence copies. A generated occurrence carries
-- recurring=FALSE (it is an instance, not itself a definition) with
-- template_id pointing back at the original row and period naming which
-- occurrence it is ('2026-08' for a monthly template, etc.) — the
-- idempotency guard is the partial unique index on (template_id, period):
-- generating twice for one month must not create a duplicate liability.
--
-- Hard-delete is never permitted, full stop — no exception for DRAFT
-- here (unlike Invoice's DRAFT, which this item's own brief does not
-- carve out). VOID is reachable from any pre-VOID status and always
-- requires a reason; "voiding an APPROVED expense" is called out
-- specifically only because reversing a committed approval is the
-- highest-stakes case, not because DRAFT/PENDING_APPROVAL are exempt
-- from needing a reason too.
CREATE TABLE expense (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title              TEXT NOT NULL,
    category           TEXT NOT NULL,
    amount_minor       BIGINT NOT NULL CHECK (amount_minor > 0),
    currency_code      TEXT NOT NULL DEFAULT 'TZS',
    expense_date       DATE NOT NULL,
    payment_method     TEXT NOT NULL CHECK (payment_method IN (
                           'CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'MOBILE_MONEY', 'BANK_TRANSFER', 'CHEQUE'
                       )),
    paid_to            TEXT NOT NULL,
    receipt_number     TEXT,
    notes              TEXT,
    -- Object store key only — root CLAUDE.md-equivalent discipline for
    -- this item: "receipt files to the object store, never a blob."
    receipt_file_key   TEXT,
    recurring          BOOLEAN NOT NULL DEFAULT FALSE,
    frequency          TEXT CHECK (frequency IN ('MONTHLY', 'QUARTERLY', 'ANNUALLY')),
    template_id        UUID REFERENCES expense(id),
    period              TEXT,
    status             TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'VOID')),
    created_by         UUID NOT NULL REFERENCES app_user(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by        UUID REFERENCES app_user(id),
    approved_at        TIMESTAMPTZ,
    approval_note      TEXT,
    voided_by          UUID REFERENCES app_user(id),
    voided_at          TIMESTAMPTZ,
    void_reason        TEXT,
    CHECK (approved_by IS DISTINCT FROM created_by),
    CHECK (NOT recurring OR frequency IS NOT NULL),
    CHECK (template_id IS NULL OR period IS NOT NULL),
    -- One-directional: an APPROVED row must carry approver/timestamp, but
    -- a later VOID (root CLAUDE.md "Data": nothing financial is ever
    -- hard-deleted — VOID is reachable from APPROVED too) must not force
    -- those fields to clear. A biconditional here would reject exactly
    -- that transition, since approved_by/approved_at are the permanent
    -- record of who approved it, not cleared on void.
    CHECK (status != 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    CHECK (status != 'VOID' OR (voided_by IS NOT NULL AND voided_at IS NOT NULL AND void_reason IS NOT NULL))
);

CREATE UNIQUE INDEX idx_expense_template_period ON expense (template_id, period) WHERE template_id IS NOT NULL;
CREATE INDEX idx_expense_status ON expense (status);
CREATE INDEX idx_expense_category ON expense (category);

-- Minimal staff record: 18-admin.md §9 needs employment_type to drive
-- payroll deductions and §4.1 says explicitly "a staff record with a job
-- title and no system role is valid — not everyone employed needs a
-- login," so this cannot simply be a column on app_user. Deliberately
-- minimal — job_title, license_number, ward scope and the rest of the HR
-- onboarding surface (18-admin.md §4.2) are a separate module this item
-- does not build; this is only what payroll needs to exist at all.
CREATE TABLE staff (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_user_id         UUID REFERENCES app_user(id),
    full_name           TEXT NOT NULL,
    employment_type     TEXT NOT NULL CHECK (employment_type IN ('PERMANENT', 'CONTRACT', 'INTERN', 'VOLUNTEER', 'LOCUM')),
    basic_salary_minor  BIGINT CHECK (basic_salary_minor IS NULL OR basic_salary_minor >= 0),
    employment_date     DATE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_staff_app_user ON staff (app_user_id);
