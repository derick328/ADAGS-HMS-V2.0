-- A proposed edit may rename a package unit (for example PACK_50 to PACK_60).
-- Keep the approved definition immutable until a second person approves it.
ALTER TABLE medicine_packaging
    ADD COLUMN replaces_packaging_id UUID REFERENCES medicine_packaging(id);

CREATE UNIQUE INDEX uq_medicine_packaging_pending_replacement
    ON medicine_packaging (replaces_packaging_id)
    WHERE status = 'PROPOSED' AND replaces_packaging_id IS NOT NULL;
