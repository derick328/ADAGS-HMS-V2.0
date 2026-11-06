-- Legacy dispense_line.quantity remains an exact count of Item.unit.
-- New selections retain the original quantity and conversion at dispense time.
ALTER TABLE dispense_line
    ADD COLUMN requested_quantity INTEGER,
    ADD COLUMN requested_unit TEXT,
    ADD COLUMN conversion_factor INTEGER NOT NULL DEFAULT 1;

UPDATE dispense_line dl SET requested_quantity = dl.quantity, requested_unit = i.unit
FROM item i WHERE i.id = dl.item_id;

ALTER TABLE dispense_line
    ALTER COLUMN requested_quantity SET NOT NULL,
    ALTER COLUMN requested_unit SET NOT NULL,
    ADD CONSTRAINT ck_dispense_unit_snapshot CHECK
      (requested_quantity > 0 AND conversion_factor > 0
       AND quantity = requested_quantity * conversion_factor);
