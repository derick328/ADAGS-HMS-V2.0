ALTER TABLE diagnosis
    ADD COLUMN voided_at TIMESTAMPTZ,
    ADD COLUMN voided_by UUID REFERENCES app_user(id),
    ADD COLUMN void_reason TEXT;

ALTER TABLE diagnosis
    ADD CONSTRAINT diagnosis_void_metadata_together
    CHECK ((voided_at IS NULL) = (voided_by IS NULL)
       AND (voided_at IS NULL) = (void_reason IS NULL));

DROP INDEX idx_diagnosis_one_primary;
CREATE UNIQUE INDEX idx_diagnosis_one_primary ON diagnosis (encounter_id)
    WHERE is_primary AND voided_at IS NULL;
