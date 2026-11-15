-- Preserve existing units and quantities while allowing measured medicine amounts.
-- NUMERIC keeps ledger arithmetic exact; discrete package/blister counts stay integers.
ALTER TABLE medicine_packaging ALTER COLUMN quantity_in_base_units TYPE NUMERIC(20,6);
ALTER TABLE stock_receipt_line
    ALTER COLUMN quantity_in_base_units TYPE NUMERIC(20,6),
    ALTER COLUMN accepted_base_quantity TYPE NUMERIC(20,6),
    ALTER COLUMN rejected_base_quantity TYPE NUMERIC(20,6);
ALTER TABLE stock_batch ALTER COLUMN dispense_multiple_base TYPE NUMERIC(20,6);
ALTER TABLE stock_balance ALTER COLUMN quantity TYPE NUMERIC(20,6);
ALTER TABLE stock_movement ALTER COLUMN qty_delta TYPE NUMERIC(20,6);
ALTER TABLE stock_adjustment ALTER COLUMN quantity_delta TYPE NUMERIC(20,6);
ALTER TABLE prescription_line
    ALTER COLUMN quantity_prescribed TYPE NUMERIC(20,6),
    ALTER COLUMN quantity_dispensed TYPE NUMERIC(20,6);
ALTER TABLE prescription_unit_snapshot
    ALTER COLUMN requested_quantity TYPE NUMERIC(20,6),
    ALTER COLUMN conversion_factor TYPE NUMERIC(20,6);
ALTER TABLE dispense ALTER COLUMN quantity_dispensed TYPE NUMERIC(20,6);
ALTER TABLE dispense_line
    ALTER COLUMN quantity TYPE NUMERIC(20,6),
    ALTER COLUMN requested_quantity TYPE NUMERIC(20,6),
    ALTER COLUMN conversion_factor TYPE NUMERIC(20,6);
ALTER TABLE invoice_line ALTER COLUMN quantity TYPE NUMERIC(20,6);
ALTER TABLE lab_stock_request
    ALTER COLUMN requested_quantity TYPE NUMERIC(20,6),
    ALTER COLUMN released_quantity TYPE NUMERIC(20,6);
ALTER TABLE procedure_item
    ALTER COLUMN quantity_prepared TYPE NUMERIC(20,6),
    ALTER COLUMN quantity_used TYPE NUMERIC(20,6),
    ALTER COLUMN quantity_returned TYPE NUMERIC(20,6),
    ALTER COLUMN quantity_wasted TYPE NUMERIC(20,6);

ALTER TABLE stock_batch ADD COLUMN can_split_package BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE stock_batch b SET can_split_package = p.can_split
FROM stock_receipt_line l JOIN medicine_packaging p ON p.id = l.packaging_id
WHERE b.receipt_line_id = l.id;
