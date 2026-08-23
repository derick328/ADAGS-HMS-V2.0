-- Phase 8, surgery module, item 8: emergency draft mode.
-- 04-*.md §5, root CLAUDE.md task text.
--
-- DRAFT_EMERGENCY is a genuinely different creation path (CreateEmergencyDraft
-- SurgeryOrderService): urgency = IMMEDIATE, no consent gate, no catalogue
-- lookup, no charge posted — "billing gate bypassed." procedure_service_item_code
-- and invoice_line_id must therefore both become nullable (V50 had them
-- NOT NULL), with a CHECK ensuring every OTHER status still has both — the
-- moment a draft is reconciled to a catalogue item
-- (ReconcileEmergencyDraftProcedureNameService), it moves to IN_PROGRESS
-- and both become populated, same as the normal ScheduleSurgeryService path.
--
-- governance_escalated_at: "48h -> CLINICAL_LEAD governance queue," set
-- once by EmergencyDraftGovernanceJob so a draft is alerted once, not
-- every job run — "Never auto-closed" means this column only ever records
-- that an alert fired, never anything that changes status.
ALTER TABLE surgery_order DROP CONSTRAINT surgery_order_status_check;
ALTER TABLE surgery_order ADD CONSTRAINT surgery_order_status_check
    CHECK (status IN ('DRAFT_EMERGENCY', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

ALTER TABLE surgery_order ALTER COLUMN procedure_service_item_code DROP NOT NULL;
ALTER TABLE surgery_order ALTER COLUMN invoice_line_id DROP NOT NULL;

ALTER TABLE surgery_order ADD CONSTRAINT surgery_order_draft_reconciled_before_use
    CHECK (status = 'DRAFT_EMERGENCY' OR (procedure_service_item_code IS NOT NULL AND invoice_line_id IS NOT NULL));

ALTER TABLE surgery_order ADD COLUMN governance_escalated_at TIMESTAMPTZ;
