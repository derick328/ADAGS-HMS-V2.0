-- Phase 8, surgery module, item 5: post-procedure disposition.
-- 04-procedure-bedday-rules.md §2.4, root CLAUDE.md task text.
--
-- Recorded once, after COMPLETED (RecordSurgeryDispositionService enforces
-- the status precondition; the WHERE-guarded UPDATE below is the race
-- guard). disposition_recorded_by/at are both null exactly when
-- disposition is — a single cross-column CHECK, since ADD COLUMN can't
-- carry a multi-column constraint inline the way CREATE TABLE can.
ALTER TABLE surgery_order ADD COLUMN disposition TEXT
    CHECK (disposition IN ('RECOVERY_THEN_DISCHARGE', 'ADMIT_TO_WARD', 'TRANSFER_OUT', 'DECEASED'));
ALTER TABLE surgery_order ADD COLUMN disposition_recorded_by UUID REFERENCES app_user(id);
ALTER TABLE surgery_order ADD COLUMN disposition_recorded_at TIMESTAMPTZ;

ALTER TABLE surgery_order ADD CONSTRAINT surgery_order_disposition_consistency
    CHECK ((disposition IS NULL) = (disposition_recorded_by IS NULL)
       AND (disposition IS NULL) = (disposition_recorded_at IS NULL));
