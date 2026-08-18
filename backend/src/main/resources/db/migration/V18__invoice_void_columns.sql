-- Phase 3, billing gate, item 5: voiding an unpaid consultation charge
-- when a patient in AWAITING_PAYMENT leaves without paying (row 8 of the
-- encounter transition table). 02-billing-architecture.md §10: "A VOID
-- invoice is never deleted... a gap in the sequence with no explanation
-- is what an auditor looks for" — the reason is what closes that gap.

ALTER TABLE invoice
    ADD COLUMN void_reason TEXT,
    ADD COLUMN voided_by   UUID REFERENCES app_user(id),
    ADD COLUMN voided_at   TIMESTAMPTZ;

ALTER TABLE invoice
    ADD CONSTRAINT chk_invoice_void_fields
    CHECK (state != 'VOID' OR (void_reason IS NOT NULL AND voided_by IS NOT NULL AND voided_at IS NOT NULL));
