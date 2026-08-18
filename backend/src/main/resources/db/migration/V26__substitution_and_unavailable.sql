-- Phase 5, pharmacy remaining pieces item 1: out-of-stock and
-- substitution. docs/spec/12-pharmacist.md §5.
--
-- substitution_allowed is prescriber-set, at prescribing time — V25's own
-- comment flagged this as later work, now built. Defaults FALSE: a
-- substitution proposal is only possible when the prescriber explicitly
-- allowed it, not by default.

ALTER TABLE prescription_line
    ADD COLUMN substitution_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    -- Why a CANCELLED or UNAVAILABLE line got there — required by the
    -- application layer on both transitions (MarkUnavailableService,
    -- substitution approval), not by a CHECK here for the same reason
    -- fefo_override_reason on dispense_line isn't: recording *that* a
    -- reason is required for two specific state transitions among several
    -- possible ones isn't expressible as a single-row CHECK.
    ADD COLUMN status_reason TEXT;

ALTER TABLE prescription_line DROP CONSTRAINT prescription_line_status_check;
ALTER TABLE prescription_line ADD CONSTRAINT prescription_line_status_check
    CHECK (status IN ('AWAITING_DISPENSE', 'PARTIALLY_DISPENSED', 'COMPLETED', 'CANCELLED', 'UNAVAILABLE'));

-- §5.1: "The prescriber's substitution_allowed flag governs whether a
-- proposal is possible at all." proposed_item_id carries no DB FK —
-- inventory is a different module, same treatment as prescription_line.item_id.
--
-- reviewed_by != proposed_by: the same second-person-constraint pattern
-- root CLAUDE.md "Authorisation" and V24's item_classification_change
-- apply elsewhere — a pharmacist proposing a substitution should not be
-- able to also approve their own proposal, even though in practice the
-- reviewer is a doctor and the proposer a pharmacist.
CREATE TABLE substitution_proposal (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_line_id UUID NOT NULL REFERENCES prescription_line(id),
    proposed_item_id     UUID NOT NULL,
    reason               TEXT NOT NULL,
    proposed_by          UUID NOT NULL REFERENCES app_user(id),
    proposed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    status               TEXT NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED')),
    reviewed_by          UUID REFERENCES app_user(id),
    reviewed_at          TIMESTAMPTZ,
    review_note          TEXT,
    -- Set on approval — the new PrescriptionLine created for proposed_item_id.
    replacement_line_id  UUID REFERENCES prescription_line(id),
    CHECK (reviewed_by IS NULL OR reviewed_by != proposed_by),
    CHECK ((status = 'PROPOSED' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);

CREATE INDEX idx_substitution_proposal_line ON substitution_proposal (prescription_line_id);
