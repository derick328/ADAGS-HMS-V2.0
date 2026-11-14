ALTER TABLE pharmacy_item_proposal DROP CONSTRAINT pharmacy_item_proposal_proposal_type_check;
ALTER TABLE pharmacy_item_proposal ADD CONSTRAINT pharmacy_item_proposal_proposal_type_check
    CHECK (proposal_type IN ('NEW_ITEM', 'PRICE_CHANGE', 'FORM_CHANGE'));
ALTER TABLE pharmacy_item_proposal ADD CONSTRAINT ck_form_change_details
    CHECK (proposal_type <> 'FORM_CHANGE' OR
           (item_id IS NOT NULL AND proposed_form IS NOT NULL AND proposed_unit IS NOT NULL
            AND justification IS NOT NULL
            AND length(trim(justification)) > 0));

ALTER TABLE prescription_unit_snapshot ADD COLUMN dosage_form TEXT;
ALTER TABLE prescription_unit_snapshot ADD COLUMN product_strength TEXT;
UPDATE prescription_unit_snapshot s SET dosage_form = i.form, product_strength = i.strength
FROM prescription_line l JOIN item i ON i.id = l.item_id
WHERE s.prescription_line_id = l.id;
