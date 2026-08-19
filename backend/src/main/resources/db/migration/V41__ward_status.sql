-- Phase 6, item 4: ward care execution, ward status. 17-ward-nurse.md §9.
--
-- No WardPatientAssignment/status concept exists anywhere yet (confirmed by
-- grep). ward_status lives on admission rather than a new table — one
-- admission has exactly one current status, the same relationship
-- discharge_ordered_at/actual_departure_at already have to this row.
--
-- ward_status_suggested_critical_at (§4.3: "auto-suggested... nurse
-- confirms") is deliberately NOT the same act as setting ward_status to
-- CRITICAL — an alerting vital proposes, it does not flip the chart. The
-- nurse's own status-update call is what actually changes ward_status and
-- clears this column, whether she agrees (sets CRITICAL herself) or not.
ALTER TABLE admission
    ADD COLUMN ward_status TEXT NOT NULL DEFAULT 'ADMITTED'
        CHECK (ward_status IN ('ADMITTED', 'STABLE', 'CRITICAL', 'DISCHARGED', 'TRANSFERRED')),
    ADD COLUMN ward_status_updated_at TIMESTAMPTZ,
    ADD COLUMN ward_status_updated_by UUID REFERENCES app_user(id),
    ADD COLUMN ward_status_suggested_critical_at TIMESTAMPTZ;
