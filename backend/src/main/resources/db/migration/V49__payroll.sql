-- Phase 7, item 4 (part 2): payroll. 18-admin.md §9 (R6 — employment-
-- type-driven deductions), §5.1 (bulk disbursement).
--
-- ⚠ Seed NO deduction rates. They come from TRA, change with each finance
-- act, and a wrong rate is an under-withholding liability — same
-- reasoning V15 gives for seeding only CASH prices, and V46 for seeding
-- no coverage rules.
--
-- Same effective_from/effective_to versioning as SchemeCoverageRule and
-- PriceListEntry — a payslip generated for August must be reconstructable
-- against whichever rule applied in August, not whatever rule is current
-- when someone looks at it later. rate_or_amount is NUMERIC, never
-- FLOAT/DOUBLE (root CLAUDE.md "Money"), meaning depends on
-- calculation_method: a 0-100 percentage for PERCENTAGE, minor units for
-- FIXED, and for BAND the per-band rate is out of scope for this simple
-- rate_or_amount column — banded calculation is not implemented in this
-- item; the DeductionRule structure supports the shape (18-admin.md §9.2
-- itself: "the DeductionRule structure supports either answer") but band
-- tables/breakpoints are a later refinement.
CREATE TABLE deduction_rule (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employment_type     TEXT NOT NULL CHECK (employment_type IN ('PERMANENT', 'CONTRACT', 'INTERN', 'VOLUNTEER', 'LOCUM')),
    deduction_code      TEXT NOT NULL,
    calculation_method  TEXT NOT NULL CHECK (calculation_method IN ('PERCENTAGE', 'BAND', 'FIXED')),
    rate_or_amount      NUMERIC(14, 4) NOT NULL,
    threshold_minor     BIGINT CHECK (threshold_minor IS NULL OR threshold_minor >= 0),
    -- 18-admin.md §9.1: "these are different things and must not both
    -- appear as payslip deductions" — employee-deducted (PAYE, NSSF/PSSSF
    -- employee, NHIF employee) reduces net pay; employer-paid (SDL, WCF,
    -- NSSF/PSSSF employer) is a hospital expense, never a payslip line.
    is_employer_paid    BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from      DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to        DATE,
    created_by          UUID NOT NULL REFERENCES app_user(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- One current rule per (employment_type, deduction_code) — same
-- current-row pattern as price_list_entry/provider_billing_category/
-- scheme_coverage_rule.
CREATE UNIQUE INDEX idx_deduction_rule_current ON deduction_rule (employment_type, deduction_code)
    WHERE effective_to IS NULL;
CREATE INDEX idx_deduction_rule_employment_type ON deduction_rule (employment_type);

-- 18-admin.md §5.1: "Bulk-disburse is the largest single financial action
-- in the system. It shows what it is about to do before it does it" —
-- headcount/gross/deductions/net are the preview's numbers, persisted
-- here once committed so the batch remains self-describing without
-- re-deriving from salary_record. UNIQUE(batch_type, month, year) is the
-- idempotency guard: running the generator twice for one month must not
-- pay twice.
CREATE TABLE salary_disbursement_batch (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_type               TEXT NOT NULL DEFAULT 'SALARY',
    month                    INT NOT NULL CHECK (month BETWEEN 1 AND 12),
    year                     INT NOT NULL,
    headcount                INT NOT NULL,
    gross_total_minor        BIGINT NOT NULL,
    deductions_total_minor   BIGINT NOT NULL,
    net_total_minor          BIGINT NOT NULL,
    currency_code            TEXT NOT NULL DEFAULT 'TZS',
    status                   TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DISBURSED', 'REVERSED')),
    generated_by             UUID NOT NULL REFERENCES app_user(id),
    generated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    disbursed_by             UUID REFERENCES app_user(id),
    disbursed_at             TIMESTAMPTZ,
    reversed_by              UUID REFERENCES app_user(id),
    reversed_at              TIMESTAMPTZ,
    reversal_reason          TEXT,
    CHECK (disbursed_by IS DISTINCT FROM generated_by),
    -- One-directional, not a biconditional: a DISBURSED batch must carry
    -- disburser/timestamp, but a later REVERSED (18-admin.md §5.1:
    -- "single reversible batch") must not force them to clear — they are
    -- the permanent record of who disbursed it, not cleared on reversal.
    CHECK (status != 'DISBURSED' OR (disbursed_by IS NOT NULL AND disbursed_at IS NOT NULL)),
    CHECK (status != 'REVERSED' OR (reversed_by IS NOT NULL AND reversed_at IS NOT NULL AND reversal_reason IS NOT NULL))
);

CREATE UNIQUE INDEX idx_salary_disbursement_batch_period ON salary_disbursement_batch (batch_type, month, year);

CREATE TABLE salary_record (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id              UUID NOT NULL REFERENCES staff(id),
    month                 INT NOT NULL CHECK (month BETWEEN 1 AND 12),
    year                  INT NOT NULL,
    basic_minor           BIGINT NOT NULL CHECK (basic_minor >= 0),
    overtime_hours        NUMERIC(6, 2) NOT NULL DEFAULT 0 CHECK (overtime_hours >= 0),
    overtime_rate_minor   BIGINT NOT NULL DEFAULT 0 CHECK (overtime_rate_minor >= 0),
    gross_minor           BIGINT NOT NULL CHECK (gross_minor >= 0),
    -- {code, amount_minor, rule_id} per line — 18-admin.md §9.3: "stores
    -- the rule_id applied, so a historical payslip is reconstructable
    -- even after rates change."
    deductions            JSONB NOT NULL DEFAULT '[]'::jsonb,
    net_minor             BIGINT NOT NULL CHECK (net_minor >= 0),
    currency_code         TEXT NOT NULL DEFAULT 'TZS',
    payment_method        TEXT CHECK (payment_method IN (
                              'CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'MOBILE_MONEY', 'BANK_TRANSFER', 'CHEQUE'
                          )),
    payment_status        TEXT NOT NULL DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING', 'PAID')),
    paid_at               TIMESTAMPTZ,
    notes                 TEXT,
    batch_id              UUID REFERENCES salary_disbursement_batch(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- gross = basic + overtime is an application-layer computation
    -- (SalaryRecord.calculate), not a database invariant — fractional
    -- overtime_hours rounded to whole minor units at computation time
    -- would make an exact-equality CHECK here fragile against legitimate
    -- rounding, not a real data error to reject.
    CHECK ((payment_status = 'PAID') = (paid_at IS NOT NULL))
);

-- One salary record per staff member per period — a second generation
-- run for the same month must not create a duplicate liability.
CREATE UNIQUE INDEX idx_salary_record_staff_period ON salary_record (staff_id, month, year);
CREATE INDEX idx_salary_record_batch ON salary_record (batch_id);

-- 18-admin.md §5.2: "PAYROLL_MANAGE as its own permission even if admin
-- holds it today" — already true (V5: PAYROLL_MANAGE exists standalone,
-- granted only to ADMIN). Nothing to change here; noted for the record.
