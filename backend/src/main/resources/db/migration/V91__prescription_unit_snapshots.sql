-- Keep quantity_prescribed and quantity_dispensed in the existing base unit.
-- Existing rows are explicitly interpreted as one base unit per requested unit.
CREATE TABLE prescription_unit_snapshot (
    prescription_line_id UUID PRIMARY KEY REFERENCES prescription_line(id),
    requested_quantity INTEGER NOT NULL CHECK (requested_quantity > 0),
    requested_unit TEXT NOT NULL,
    conversion_factor INTEGER NOT NULL CHECK (conversion_factor > 0),
    base_unit TEXT NOT NULL,
    packaging_id UUID,
    CHECK (requested_quantity * conversion_factor > 0)
);

INSERT INTO prescription_unit_snapshot
  (prescription_line_id, requested_quantity, requested_unit, conversion_factor, base_unit)
SELECT pl.id, pl.quantity_prescribed, i.unit, 1, i.unit
FROM prescription_line pl JOIN item i ON i.id = pl.item_id;
