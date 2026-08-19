-- Phase 7, item 2: invoice cancellation lifecycle. 18-admin.md §6.
--
-- review_note is NOT NULL on REJECTED — 18-admin.md §7.6's reasoning
-- ("reject requires a reason, not optional — a silent rejection tells
-- the proposer nothing") applies equally here: "admin's note shown to
-- the receptionist" is the whole point of a rejection.
CREATE TABLE invoice_cancellation_request (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id              UUID NOT NULL REFERENCES invoice(id),
    requested_by            UUID NOT NULL REFERENCES app_user(id),
    reason                  TEXT NOT NULL,
    requested_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                  TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by             UUID REFERENCES app_user(id),
    reviewed_at             TIMESTAMPTZ,
    review_note             TEXT,
    -- §6.1: reconsideration of a rejected request is a NEW row
    -- referencing the first, never a reopened decision.
    supersedes_request_id   UUID REFERENCES invoice_cancellation_request(id),
    CHECK ((status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CHECK (status != 'REJECTED' OR review_note IS NOT NULL)
);

-- root CLAUDE.md "Concurrency": "One PENDING request per invoice at a
-- time" is a database constraint, not an application check — two
-- receptionists racing to raise a request against the same invoice must
-- not both succeed.
CREATE UNIQUE INDEX idx_invoice_cancellation_request_one_pending
    ON invoice_cancellation_request (invoice_id) WHERE status = 'PENDING';
CREATE INDEX idx_invoice_cancellation_request_invoice ON invoice_cancellation_request (invoice_id);
CREATE INDEX idx_invoice_cancellation_request_status ON invoice_cancellation_request (status);

-- 18-admin.md §6.1: "Admin changes their mind -> raises a new
-- InvoiceCancellationRequest" — reconsideration is admin-initiated, so
-- ADMIN needs INVOICE_CANCEL_REQUEST (V5) too, alongside RECEPTIONIST who
-- already holds it.
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
 WHERE r.code = 'ADMIN' AND p.code = 'INVOICE_CANCEL_REQUEST'
ON CONFLICT DO NOTHING;
