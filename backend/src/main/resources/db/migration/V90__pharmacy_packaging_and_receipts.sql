-- Item.unit remains the canonical base stock unit. Existing quantities and
-- batches are unchanged. Package definitions are immutable once approved;
-- a replacement is a new version, never an update to an old factor.
CREATE TABLE medicine_packaging (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES item(id),
    packaging_unit TEXT NOT NULL CHECK (packaging_unit = upper(packaging_unit)),
    quantity_in_base_units INTEGER NOT NULL CHECK (quantity_in_base_units > 1),
    can_split BOOLEAN NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    status TEXT NOT NULL CHECK (status IN ('PROPOSED', 'APPROVED', 'SUPERSEDED', 'REJECTED')),
    reason TEXT NOT NULL,
    proposed_by UUID NOT NULL REFERENCES app_user(id),
    proposed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by UUID REFERENCES app_user(id),
    approved_at TIMESTAMPTZ,
    UNIQUE (item_id, packaging_unit, version),
    CHECK (approved_by IS NULL OR approved_by <> proposed_by),
    CHECK ((status = 'APPROVED') = (approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR status = 'SUPERSEDED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
);
CREATE UNIQUE INDEX uq_medicine_packaging_approved
    ON medicine_packaging (item_id, packaging_unit) WHERE status = 'APPROVED';
CREATE UNIQUE INDEX uq_medicine_packaging_proposed
    ON medicine_packaging (item_id, packaging_unit) WHERE status = 'PROPOSED';

CREATE TABLE stock_receipt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id UUID NOT NULL REFERENCES facility(id),
    store_id UUID NOT NULL REFERENCES store(id),
    supplier_id UUID NOT NULL REFERENCES supplier(id),
    receipt_number TEXT NOT NULL,
    invoice_number TEXT NOT NULL,
    delivery_note_number TEXT NOT NULL,
    invoice_date DATE NOT NULL,
    delivery_date DATE NOT NULL,
    notes TEXT,
    idempotency_key TEXT NOT NULL UNIQUE,
    request_fingerprint TEXT NOT NULL,
    received_by UUID NOT NULL REFERENCES app_user(id),
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    total_received_cost_minor BIGINT NOT NULL CHECK (total_received_cost_minor >= 0),
    total_accepted_cost_minor BIGINT NOT NULL CHECK (total_accepted_cost_minor >= 0),
    currency_code TEXT NOT NULL DEFAULT 'TZS',
    UNIQUE (facility_id, receipt_number)
);
CREATE INDEX idx_stock_receipt_facility_date ON stock_receipt (facility_id, confirmed_at DESC);

CREATE TABLE stock_receipt_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id UUID NOT NULL REFERENCES stock_receipt(id),
    line_number INTEGER NOT NULL CHECK (line_number > 0),
    item_id UUID NOT NULL REFERENCES item(id),
    packaging_id UUID REFERENCES medicine_packaging(id),
    purchase_unit TEXT NOT NULL,
    quantity_in_base_units INTEGER NOT NULL CHECK (quantity_in_base_units > 0),
    ordered_quantity INTEGER CHECK (ordered_quantity > 0),
    received_quantity INTEGER NOT NULL CHECK (received_quantity >= 0),
    accepted_quantity INTEGER NOT NULL CHECK (accepted_quantity >= 0),
    rejected_quantity INTEGER NOT NULL CHECK (rejected_quantity >= 0),
    missing_quantity INTEGER NOT NULL CHECK (missing_quantity >= 0),
    accepted_base_quantity INTEGER NOT NULL CHECK (accepted_base_quantity >= 0),
    rejected_base_quantity INTEGER NOT NULL CHECK (rejected_base_quantity >= 0),
    batch_number TEXT NOT NULL,
    expiry_date DATE NOT NULL,
    unit_purchase_cost_minor BIGINT NOT NULL CHECK (unit_purchase_cost_minor >= 0),
    received_cost_minor BIGINT NOT NULL CHECK (received_cost_minor >= 0),
    accepted_cost_minor BIGINT NOT NULL CHECK (accepted_cost_minor >= 0),
    currency_code TEXT NOT NULL DEFAULT 'TZS',
    discrepancy_reason TEXT,
    notes TEXT,
    UNIQUE (receipt_id, line_number),
    CHECK (received_quantity = accepted_quantity + rejected_quantity),
    CHECK (ordered_quantity IS NOT NULL OR missing_quantity = 0),
    CHECK (ordered_quantity IS NULL OR missing_quantity = greatest(ordered_quantity - received_quantity, 0)),
    CHECK (accepted_base_quantity = accepted_quantity * quantity_in_base_units),
    CHECK (rejected_base_quantity = rejected_quantity * quantity_in_base_units),
    CHECK (received_cost_minor = received_quantity * unit_purchase_cost_minor),
    CHECK (accepted_cost_minor = accepted_quantity * unit_purchase_cost_minor),
    CHECK (discrepancy_reason IS NOT NULL OR (rejected_quantity = 0 AND missing_quantity = 0
           AND (ordered_quantity IS NULL OR received_quantity = ordered_quantity)))
);
CREATE INDEX idx_stock_receipt_line_receipt ON stock_receipt_line (receipt_id);

ALTER TABLE stock_batch ADD COLUMN receipt_line_id UUID REFERENCES stock_receipt_line(id);
-- For an unsplittable purchased package, dispensing must be a multiple of
-- its original base-unit factor. Legacy batches retain their old behavior.
ALTER TABLE stock_batch ADD COLUMN dispense_multiple_base INTEGER NOT NULL DEFAULT 1
    CHECK (dispense_multiple_base > 0);

-- The receipt line links accepted and quarantined cost tranches. A split into
-- floor- and ceiling-cost tranches keeps every unit cost an integer while
-- preserving the exact package purchase value in stock valuation and COGS.
CREATE INDEX idx_stock_batch_receipt_line ON stock_batch (receipt_line_id);

INSERT INTO permission (code, module, description, is_clinical)
VALUES
    ('PACKAGING_PROPOSE', 'inventory', 'Propose a product packaging conversion', FALSE),
    ('PACKAGING_APPROVE', 'inventory', 'Approve a product packaging conversion', FALSE),
    ('STOCK_ADJUST_REQUEST', 'inventory', 'Request a stock correction', FALSE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'PHARMACIST' AND p.code IN ('PACKAGING_PROPOSE', 'STOCK_ADJUST_REQUEST')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code IN ('ADMIN', 'ACCOUNTANT') AND p.code = 'PACKAGING_APPROVE'
ON CONFLICT DO NOTHING;
