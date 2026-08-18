-- Phase 6, item 2: the nightly bed-day accrual ledger.
-- 04-procedure-bedday-rules.md §3.5. One row per occupied bed-day.
--
-- invoice_line_id is nullable so the posting service can RESERVE a row
-- (claiming the unique (bed_assignment_id, accrual_boundary_at) slot)
-- before calling into billing to create the actual charge, then attach the
-- resulting line — both inside one transaction, so a failure between the
-- two rolls the reservation back with it rather than leaving an orphan.
-- deposit_transaction_id from the original spec table is omitted: the
-- deposit ledger (07-doctor.md §11) is not built yet.
CREATE TABLE ward_charge_accrual (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id        UUID NOT NULL REFERENCES admission(id),
    bed_assignment_id   UUID NOT NULL REFERENCES bed_assignment(id),
    ward_id             UUID NOT NULL REFERENCES ward(id),
    accrual_boundary_at TIMESTAMPTZ NOT NULL,
    daily_rate_minor    BIGINT NOT NULL CHECK (daily_rate_minor >= 0),
    currency_code       TEXT NOT NULL DEFAULT 'TZS',
    invoice_line_id     UUID REFERENCES invoice_line(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- root CLAUDE.md "Concurrency": "a rerun after a failure must not
-- double-bill." The real guard — a read-then-write check in the job
-- loses the race the same way any other one would.
CREATE UNIQUE INDEX idx_ward_charge_accrual_unique ON ward_charge_accrual (bed_assignment_id, accrual_boundary_at);

CREATE INDEX idx_ward_charge_accrual_admission ON ward_charge_accrual (admission_id, accrual_boundary_at);
