-- A manufacturer's pack can contain one blister with one base unit.
-- Keep the purchase unit distinct so its price and presentation remain explicit.
ALTER TABLE medicine_packaging
    DROP CONSTRAINT medicine_packaging_quantity_in_base_units_check;
ALTER TABLE medicine_packaging
    ADD CONSTRAINT medicine_packaging_quantity_in_base_units_check
        CHECK (quantity_in_base_units > 0);
