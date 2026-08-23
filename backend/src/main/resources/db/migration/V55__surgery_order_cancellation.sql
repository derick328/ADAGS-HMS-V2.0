-- Phase 8, surgery module, item 6: cancellation.
-- 04-*.md §6, root CLAUDE.md task text.
--
-- cancellation_reason/cancelled_by/cancelled_at are null exactly when the
-- order was never cancelled — same cross-column CHECK idiom as V54's
-- disposition columns.
ALTER TABLE surgery_order ADD COLUMN cancellation_reason TEXT
    CHECK (cancellation_reason IN (
        'PATIENT_REQUEST', 'PATIENT_UNFIT', 'NO_SHOW', 'SURGEON_UNAVAILABLE', 'THEATRE_UNAVAILABLE',
        'EQUIPMENT_FAILURE', 'STAFF_UNAVAILABLE', 'CLINICAL_DECISION'
    ));
ALTER TABLE surgery_order ADD COLUMN cancelled_by UUID REFERENCES app_user(id);
ALTER TABLE surgery_order ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE surgery_order ADD CONSTRAINT surgery_order_cancellation_consistency
    CHECK ((cancellation_reason IS NULL) = (cancelled_by IS NULL)
       AND (cancellation_reason IS NULL) = (cancelled_at IS NULL));
