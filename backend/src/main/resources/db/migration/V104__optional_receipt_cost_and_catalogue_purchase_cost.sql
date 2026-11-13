-- Unknown supplier cost remains distinct from an entered zero. Existing rows
-- had an explicit cost under the old contract and keep their original values.
ALTER TABLE stock_receipt_line ALTER COLUMN unit_purchase_cost_minor DROP NOT NULL;
ALTER TABLE stock_receipt_line ALTER COLUMN received_cost_minor DROP NOT NULL;
ALTER TABLE stock_receipt_line ALTER COLUMN accepted_cost_minor DROP NOT NULL;
ALTER TABLE stock_receipt ALTER COLUMN total_received_cost_minor DROP NOT NULL;
ALTER TABLE stock_receipt ALTER COLUMN total_accepted_cost_minor DROP NOT NULL;
ALTER TABLE stock_batch ADD COLUMN cost_known BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE stock_batch ADD CONSTRAINT ck_stock_batch_unknown_cost_placeholder
    CHECK (cost_known OR unit_cost_amount_minor = 0);
ALTER TABLE dispense_line ADD COLUMN cost_known BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE medicine_packaging ADD COLUMN blisters_per_pack INTEGER;
ALTER TABLE medicine_packaging ADD COLUMN units_per_blister INTEGER;
ALTER TABLE medicine_packaging ADD CONSTRAINT ck_medicine_packaging_blister_hierarchy CHECK (
    (blisters_per_pack IS NULL AND units_per_blister IS NULL)
    OR (blisters_per_pack > 0 AND units_per_blister > 0
        AND quantity_in_base_units = blisters_per_pack * units_per_blister)
);

CREATE TABLE medicine_default_purchase_cost (
    item_id UUID PRIMARY KEY REFERENCES item(id),
    unit TEXT NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
    updated_by UUID NOT NULL REFERENCES app_user(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE medicine_default_purchase_cost_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES item(id),
    unit TEXT NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
    changed_by UUID NOT NULL REFERENCES app_user(id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_medicine_default_cost_history_item
    ON medicine_default_purchase_cost_history (item_id, changed_at DESC);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'ADMIN' AND p.code = 'PACKAGING_PROPOSE'
ON CONFLICT DO NOTHING;
