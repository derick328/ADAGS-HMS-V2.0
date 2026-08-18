-- Phase 5, pharmacy remaining pieces item 2: lab stock requests.
-- docs/spec/12-pharmacist.md §7, docs/spec/15-lab-technician.md.
--
-- Lives in the inventory module, not laboratory or pharmacy — this is
-- fundamentally a stock-transfer concern ("the same request-and-release
-- pattern will serve ward sub-stores when the ward module lands"), and
-- item_id can be a real FK here since Item lives in this same module,
-- unlike the pharmacy module's necessarily-hookless item_id columns.
--
-- ⚠ A release is a TRANSFER, not a dispense — two movement rows (negative
-- from the source store, positive into the requesting store), preserving
-- batch_id across both. stock_balance is already keyed
-- (item_id, batch_id, store_id), so one physical batch can legitimately
-- have on-hand quantity at more than one store after a transfer — this is
-- what keeps the Phase 4 reagent-lot traceability hook
-- (lab_result_reagent.stock_batch_id) meaningful once it's wired up: the
-- batch a lab result's reagent came from is still the same batch_id that
-- was received at the pharmacy, not a new row that severed the lot link.
CREATE TABLE lab_stock_request (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id              UUID NOT NULL REFERENCES item(id),
    requested_quantity   INTEGER NOT NULL CHECK (requested_quantity > 0),
    released_quantity    INTEGER NOT NULL DEFAULT 0 CHECK (released_quantity >= 0),
    requesting_store_id  UUID NOT NULL REFERENCES store(id),
    requested_by         UUID NOT NULL REFERENCES app_user(id),
    requested_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- PENDING: nothing released yet. PARTIALLY_RELEASED: some released,
    -- more could still come. RELEASED: fully satisfied. REJECTED: no
    -- release will happen, ever (from PENDING only). CLOSED: pharmacist
    -- has explicitly said no more will come, whether or not fully
    -- satisfied — "the remainder stays open or closes with a note."
    status               TEXT NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING', 'PARTIALLY_RELEASED', 'RELEASED', 'REJECTED', 'CLOSED')),
    response_notes       TEXT,
    reviewed_by          UUID REFERENCES app_user(id),
    reviewed_at          TIMESTAMPTZ,
    CHECK (released_quantity <= requested_quantity),
    -- §7: "Reject requires a note."
    CHECK (status != 'REJECTED' OR response_notes IS NOT NULL),
    CHECK (status != 'CLOSED' OR response_notes IS NOT NULL)
);

CREATE INDEX idx_lab_stock_request_item ON lab_stock_request (item_id);
CREATE INDEX idx_lab_stock_request_store ON lab_stock_request (requesting_store_id);
