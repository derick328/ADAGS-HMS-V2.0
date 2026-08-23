-- Phase 8, surgery module, item 2: ProcedureItem — consumption discovered,
-- not ordered. 04-procedure-bedday-rules.md §2, root CLAUDE.md task text.
--
-- item_id/batch_id carry no FK: inventory is a different module (root
-- CLAUDE.md "Modules": no cross-module foreign keys except to patient and
-- encounter) — same treatment as lab_order.invoice_line_id (V21). item_id
-- resolves through the inventory module's own Item catalogue, the same one
-- pharmacy dispensing uses (InventoryFacade) — no separate LAB/CONSUMABLE
-- item-type split, since nothing in this phase's task text calls for one
-- and lab's own stock (lab_stock_request) is a distinct mechanism this
-- phase does not touch.
--
-- quantity_used/quantity_returned/quantity_wasted default to 0 and are
-- filled in at reconciliation (item 3) — a freshly prepared row is not
-- balanced, and must not be: prepared = used + returned + wasted only
-- needs to hold once reconciled, not throughout the procedure, so it is
-- not a standing CHECK here (state-dependent, enforced by item 3's
-- reconciliation service, the same way "COMPLETED cannot be reached
-- without reconciliation" is a status-transition guard, not a row CHECK).
--
-- wastage requires a reason is a static invariant (true or false
-- regardless of when it's checked), so it is a CHECK here.
--
-- reconciled_by/attested_by are both nullable during the procedure — task
-- text: "CHECK (reconciled_by != attested_by) would be ideal but both may
-- be nullable during the procedure; enforce at the service layer with the
-- same pattern as other second-person checks." Deliberately no DB CHECK
-- for that rule; item 3's reconciliation service enforces it.
CREATE TABLE procedure_item (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    surgery_order_id    UUID NOT NULL REFERENCES surgery_order(id),
    item_id             UUID NOT NULL,
    phase               TEXT NOT NULL CHECK (phase IN ('PRE_OP', 'INTRA_OP', 'POST_OP')),
    quantity_prepared   INTEGER NOT NULL CHECK (quantity_prepared > 0),
    quantity_used       INTEGER NOT NULL DEFAULT 0 CHECK (quantity_used >= 0),
    quantity_returned   INTEGER NOT NULL DEFAULT 0 CHECK (quantity_returned >= 0),
    quantity_wasted     INTEGER NOT NULL DEFAULT 0 CHECK (quantity_wasted >= 0),
    wastage_reason      TEXT,
    batch_id            UUID NOT NULL,
    prepared_by         UUID NOT NULL REFERENCES app_user(id),
    prepared_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    reconciled_by       UUID REFERENCES app_user(id),
    attested_by         UUID REFERENCES app_user(id),
    reconciled_at       TIMESTAMPTZ,
    CHECK (quantity_wasted = 0 OR wastage_reason IS NOT NULL)
);

CREATE INDEX idx_procedure_item_surgery_order ON procedure_item (surgery_order_id);
