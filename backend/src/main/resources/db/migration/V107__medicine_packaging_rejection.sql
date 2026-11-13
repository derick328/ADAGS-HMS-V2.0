ALTER TABLE medicine_packaging
    ADD COLUMN rejected_by UUID REFERENCES app_user(id),
    ADD COLUMN rejected_at TIMESTAMPTZ,
    ADD COLUMN rejection_reason TEXT;

ALTER TABLE medicine_packaging
    ADD CONSTRAINT ck_medicine_packaging_rejection_review CHECK (
        (rejected_by IS NULL AND rejected_at IS NULL AND rejection_reason IS NULL)
        OR (status = 'REJECTED' AND rejected_by IS NOT NULL
            AND rejected_at IS NOT NULL AND rejection_reason IS NOT NULL
            AND length(trim(rejection_reason)) > 0)
    );
