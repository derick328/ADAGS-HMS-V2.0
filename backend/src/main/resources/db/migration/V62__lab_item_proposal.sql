-- Lab technician workspace, Screen 4: Lab Catalogue proposals.
-- docs/spec/15-lab-technician.md §10.
--
-- Mirrors pharmacy_item_proposal's shape (V30) but is not the same table:
-- a lab test proposal carries specimen/container/TAT and a parameter list
-- pharmacy's flat item shape has no room for, and the two are gated by
-- separate permission codes (LAB_ITEM_PROPOSE/LAB_ITEM_APPROVE vs
-- PHARMACY_ITEM_PROPOSE/PHARMACY_ITEM_APPROVE, both already in V5).
--
-- Deliberately NO verification_level / is_sensitive column: §10 "is_sensitive
-- is set by CLINICAL_LEAD, not the proposing technician" — same reasoning
-- V30's comment gives for omitting proposed_regulatory_schedule. The
-- proposing technician cannot self-assign the flag that decides whether
-- their own results need a second check.
--
-- Only the PROPOSED write path is built in this phase — the technician's
-- POST. The admin review action (LAB_ITEM_APPROVE, creates the real
-- lab_test + lab_test_parameter rows + price) is a later admin-workspace
-- task; reviewed_by/reviewed_at/review_note/created_lab_test_id exist now
-- so that step is additive later, no migration needed when it lands.
CREATE TABLE lab_item_proposal (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposed_name            TEXT NOT NULL,
    proposed_classification  TEXT NOT NULL,
    proposed_price_minor     BIGINT NOT NULL CHECK (proposed_price_minor >= 0),
    proposed_price_currency  TEXT NOT NULL DEFAULT 'TZS',
    notes                    TEXT,
    specimen_type            TEXT NOT NULL,
    container_type           TEXT NOT NULL,
    expected_tat_hours       INT NOT NULL CHECK (expected_tat_hours > 0),
    -- §10: "Same justification requirement as the pharmacy proposals" —
    -- nullable at the column, enforced NOT NULL/NOT BLANK at the domain
    -- layer, same split V30 uses for PRICE_CHANGE.justification.
    justification            TEXT,
    proposed_by              UUID NOT NULL REFERENCES app_user(id),
    proposed_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                   TEXT NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED')),
    reviewed_by              UUID REFERENCES app_user(id),
    reviewed_at              TIMESTAMPTZ,
    review_note              TEXT,
    created_lab_test_id      UUID REFERENCES lab_test(id),
    CHECK (reviewed_by IS NULL OR reviewed_by != proposed_by),
    CHECK ((status = 'PROPOSED' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);

-- §10: "Parameter definitions per §5.1 — a test with no parameters
-- generates an empty result form," so zero child rows is valid, not an error.
CREATE TABLE lab_item_proposal_parameter (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id  UUID NOT NULL REFERENCES lab_item_proposal(id),
    name         TEXT NOT NULL,
    unit         TEXT,
    method       TEXT,
    sort_order   INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_lab_item_proposal_parameter_proposal ON lab_item_proposal_parameter (proposal_id);
