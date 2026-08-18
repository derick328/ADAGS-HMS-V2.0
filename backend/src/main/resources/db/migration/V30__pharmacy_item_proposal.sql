-- Phase 5, pharmacy remaining pieces item 5: pharmacy item proposals.
-- docs/spec/12-pharmacist.md §9.2.
--
-- Deliberately NO proposed_regulatory_schedule column, unlike the raw
-- spec's field list. ITEM_CLASSIFY is a dedicated, separate authority
-- (pharmacist-in-charge / CLINICAL_LEAD) precisely so the person adding an
-- item to the catalogue and the person deciding it is not CONTROLLED are
-- never the same control (13-controlled-medicines-policy.md §3: "a
-- pharmacist creating an item at an arbitrary price bypasses price
-- control from the other direction" — the same reasoning applies to
-- classification). A field here that looked like it set the schedule, but
-- didn't, would be worse than no field at all. Every approved NEW_ITEM
-- starts UNCLASSIFIED, exactly like CreateItemService's direct-create
-- path — classification remains a wholly separate action afterward.
--
-- justification is nullable at the column level but required by the
-- application layer for PRICE_CHANGE specifically (not NEW_ITEM) — same
-- treatment as fefo_override_reason on dispense_line: which transitions
-- require it isn't expressible as a single CHECK independent of proposal_type.
CREATE TABLE pharmacy_item_proposal (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_type            TEXT NOT NULL CHECK (proposal_type IN ('NEW_ITEM', 'PRICE_CHANGE')),
    item_id                  UUID REFERENCES item(id),
    proposed_name            TEXT,
    proposed_classification  TEXT,
    proposed_form            TEXT,
    proposed_unit            TEXT,
    proposed_price_minor     BIGINT NOT NULL CHECK (proposed_price_minor >= 0),
    proposed_price_currency  TEXT NOT NULL DEFAULT 'TZS',
    current_price_minor      BIGINT,
    justification            TEXT,
    proposed_by              UUID NOT NULL REFERENCES app_user(id),
    proposed_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                   TEXT NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED')),
    reviewed_by              UUID REFERENCES app_user(id),
    reviewed_at              TIMESTAMPTZ,
    review_note              TEXT,
    -- Set on NEW_ITEM approval — the item this proposal created.
    created_item_id          UUID REFERENCES item(id),
    CHECK (reviewed_by IS NULL OR reviewed_by != proposed_by),
    CHECK ((status = 'PROPOSED' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CHECK (proposal_type != 'PRICE_CHANGE' OR item_id IS NOT NULL),
    CHECK (proposal_type != 'NEW_ITEM'
        OR (proposed_name IS NOT NULL AND proposed_classification IS NOT NULL
            AND proposed_form IS NOT NULL AND proposed_unit IS NOT NULL))
);

CREATE INDEX idx_pharmacy_item_proposal_item ON pharmacy_item_proposal (item_id);
