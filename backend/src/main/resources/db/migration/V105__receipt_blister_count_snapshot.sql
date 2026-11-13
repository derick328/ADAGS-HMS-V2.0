ALTER TABLE stock_receipt_line ADD COLUMN blisters_per_pack INTEGER;
ALTER TABLE stock_receipt_line ADD COLUMN units_per_blister INTEGER;
ALTER TABLE stock_receipt_line ADD CONSTRAINT ck_receipt_blister_count CHECK (
    (blisters_per_pack IS NULL AND units_per_blister IS NULL)
    OR (blisters_per_pack > 0 AND units_per_blister > 0
        AND blisters_per_pack * units_per_blister = quantity_in_base_units)
);
