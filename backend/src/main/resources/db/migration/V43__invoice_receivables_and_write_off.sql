-- Phase 7, item 1 (part 1): aged receivables and write-off.
-- docs/spec/18-admin.md §10 ("aged receivables" governance report);
-- 02-billing-architecture.md §9.3 (write_offs is a component of
-- outstandingTotal, materialised the same way payments are), §13 ("write-off
-- and refund summary" monthly report).
--
-- receivable_at is the aging clock: set once, when markReceivablePostDischarge
-- fires (never touched again — an invoice does not un-age by receiving a
-- partial payment). Nullable because every invoice that never became a
-- receivable has none.
ALTER TABLE invoice ADD COLUMN receivable_at TIMESTAMPTZ;

-- root CLAUDE.md "Billing": INVOICE_WRITE_OFF is already ACCOUNTANT's
-- (V5) — "ACCOUNTANT authority" from the brief. requires_second_signature
-- is snapshotted at request time from the threshold in effect then (same
-- reasoning as 18-admin.md §7.6: "snapshotted on the proposal so a later
-- change does not rewrite history") — a later threshold change must not
-- silently change what an already-pending write-off requires.
--
-- Below the threshold, the requester's own action is the one required
-- signature and applies immediately (status APPLIED, decided_by =
-- requested_by permitted only in that case — see the CHECK below). Above
-- it, the request lands PENDING and a second, distinct accountant must
-- decide it before it touches the invoice.
CREATE TABLE invoice_write_off (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id                  UUID NOT NULL REFERENCES invoice(id),
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
    -- root CLAUDE.md "Authorisation": the second-person constraint at the
    -- database, not just application code. Only bites when a second
    -- signature is actually required — a below-threshold write-off is
    -- deliberately self-authorized.
    CHECK (NOT requires_second_signature OR decided_by IS NULL OR decided_by != requested_by),
    CHECK ((status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status IN ('APPLIED', 'REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL))
);

CREATE INDEX idx_invoice_write_off_invoice ON invoice_write_off (invoice_id);
CREATE INDEX idx_invoice_write_off_status ON invoice_write_off (status);

-- Placeholder default, same caveat as the duplicate-detection thresholds in
-- V7: a documented starting point (100,000/= — well above the seeded
-- consultation prices of 10,000/40,000/=, so a routine small write-off
-- self-authorizes while anything large gets a second look), not a finance-
-- team-approved figure. Admin-tunable via protocol_setting precisely so it
-- can be corrected without a redeploy once one exists.
INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'billing.write_off.second_signature_threshold_minor', '10000000'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;
