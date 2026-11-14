ALTER TABLE medicine_packaging ADD COLUMN proposed_form VARCHAR(50);
ALTER TABLE medicine_packaging ADD COLUMN proposed_base_unit VARCHAR(50);
ALTER TABLE medicine_packaging ADD COLUMN parent_proposal_id UUID REFERENCES medicine_packaging(id);
ALTER TABLE medicine_packaging ADD CONSTRAINT ck_packaging_presentation_pair
    CHECK ((proposed_form IS NULL) = (proposed_base_unit IS NULL));
