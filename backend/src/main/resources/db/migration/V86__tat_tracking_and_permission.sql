-- TAT reads existing workflow timestamps; this table holds only explicit starts
-- that the existing workflow does not record. Historical rows are never backfilled.
CREATE TABLE tat_workflow_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id UUID NOT NULL REFERENCES encounter(id),
    workflow_type TEXT NOT NULL CHECK (workflow_type IN ('TRIAGE', 'PHARMACY')),
    workflow_reference_id UUID NOT NULL,
    event_type TEXT NOT NULL CHECK (event_type IN ('STARTED')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    recorded_by UUID NOT NULL REFERENCES app_user(id),
    UNIQUE (workflow_type, workflow_reference_id, event_type)
);
CREATE INDEX idx_tat_workflow_event_encounter ON tat_workflow_event (encounter_id, occurred_at);

INSERT INTO permission (code, module, description, is_clinical)
VALUES ('TAT_ANALYTICS_READ', 'admin', 'Read operational patient turnaround analytics', FALSE)
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'ADMIN' AND p.code = 'TAT_ANALYTICS_READ'
ON CONFLICT DO NOTHING;

CREATE INDEX idx_encounter_event_tat ON encounter_event (encounter_id, occurred_at, id);
CREATE INDEX idx_lab_order_tat ON lab_order (facility_id, ordered_at, encounter_id);
CREATE INDEX idx_prescription_order_tat ON prescription_order (facility_id, prescribed_at, encounter_id);
CREATE INDEX idx_specimen_tat ON specimen (lab_order_id, collected_at);
CREATE INDEX idx_lab_result_tat ON lab_result (lab_order_id, released_at);

-- Read model only: no lifecycle timestamp is copied or synthesized. Each row
-- is one real service instance, and missing events remain NULL.
CREATE VIEW tat_stage_instance AS
WITH triage AS (
    SELECT e.id AS encounter_id, 'TRIAGE'::text AS workflow_type, e.id AS reference_id,
           e.created_at AS source_at,
           (SELECT min(ev.occurred_at) FROM encounter_event ev
             WHERE ev.encounter_id = e.id AND ev.to_state = 'AWAITING_TRIAGE') AS ready_at,
           (SELECT min(te.occurred_at) FROM tat_workflow_event te
             WHERE te.workflow_type = 'TRIAGE' AND te.workflow_reference_id = e.id) AS start_at,
           (SELECT min(ta.assessed_at) FROM triage_assessment ta
             WHERE ta.encounter_id = e.id AND ta.supersedes_id IS NULL) AS completed_at,
           NULL::timestamptz AS entered_at, NULL::timestamptz AS verified_at,
           NULL::timestamptz AS received_at,
           CASE WHEN EXISTS (SELECT 1 FROM triage_assessment ta WHERE ta.encounter_id = e.id) THEN 'COMPLETED'
                WHEN e.state IN ('LEFT_WITHOUT_BEING_SEEN','ABANDONED_UNPAID','CANCELLED_IN_ERROR') THEN 'CANCELLED'
                WHEN e.state = 'AWAITING_TRIAGE' AND EXISTS
                  (SELECT 1 FROM tat_workflow_event te WHERE te.workflow_type = 'TRIAGE' AND te.workflow_reference_id = e.id) THEN 'IN_PROGRESS'
                WHEN e.state = 'AWAITING_TRIAGE' THEN 'WAITING'
                ELSE 'INCOMPLETE' END::text AS status
    FROM encounter e
), doctor AS (
    SELECT ev.encounter_id, 'DOCTOR'::text AS workflow_type, ev.id AS reference_id,
           ev.occurred_at AS source_at,
           (SELECT max(q.occurred_at) FROM encounter_event q
             WHERE q.encounter_id = ev.encounter_id AND q.to_state = 'AWAITING_CONSULT'
               AND (q.occurred_at, q.id) < (ev.occurred_at, ev.id)) AS ready_at,
           ev.occurred_at AS start_at, nx.occurred_at AS completed_at,
           NULL::timestamptz AS entered_at, NULL::timestamptz AS verified_at,
           NULL::timestamptz AS received_at,
           CASE WHEN nx.to_state = 'AWAITING_RESULTS' THEN 'AWAITING_RESULTS'
                WHEN nx.id IS NOT NULL THEN 'COMPLETED' ELSE 'IN_PROGRESS' END::text AS status
    FROM encounter_event ev
    LEFT JOIN LATERAL (
       SELECT q.id, q.to_state, q.occurred_at FROM encounter_event q
       WHERE q.encounter_id = ev.encounter_id AND (q.occurred_at, q.id) > (ev.occurred_at, ev.id)
       ORDER BY q.occurred_at, q.id LIMIT 1
    ) nx ON TRUE
    WHERE ev.to_state = 'IN_CONSULT'
), doctor_wait AS (
    SELECT e.id AS encounter_id, 'DOCTOR'::text AS workflow_type, e.id AS reference_id,
           ev.occurred_at AS source_at, ev.occurred_at AS ready_at,
           NULL::timestamptz AS start_at, NULL::timestamptz AS completed_at,
           NULL::timestamptz AS entered_at, NULL::timestamptz AS verified_at,
           NULL::timestamptz AS received_at, 'WAITING'::text AS status
    FROM encounter e JOIN LATERAL (
      SELECT occurred_at FROM encounter_event
      WHERE encounter_id = e.id AND to_state = 'AWAITING_CONSULT'
      ORDER BY occurred_at DESC, id DESC LIMIT 1
    ) ev ON TRUE WHERE e.state = 'AWAITING_CONSULT'
), lab AS (
    SELECT lo.encounter_id, 'LABORATORY'::text AS workflow_type, lo.id AS reference_id,
           lo.ordered_at AS source_at,
           CASE WHEN il.patient_portion_minor = 0 AND il.coverage_rule_id IS NULL THEN lo.ordered_at
                WHEN paid.ready_at IS NOT NULL THEN greatest(lo.ordered_at, paid.ready_at)
                WHEN pb.granted_at IS NOT NULL THEN greatest(lo.ordered_at, pb.granted_at)
                ELSE NULL END AS ready_at,
           sp.collected_at AS start_at, lr.released_at AS completed_at,
           lr.entered_at, lr.verified_at, sp.received_at,
           CASE WHEN lr.id IS NOT NULL THEN 'COMPLETED'
                WHEN il.id IS NULL THEN 'AWAITING_AUTHORIZATION'
                WHEN sa.id IS NULL AND il.patient_portion_minor > 0 AND paid.ready_at IS NULL AND pb.granted_at IS NULL THEN 'AWAITING_PAYMENT'
                WHEN sa.id IS NULL AND il.coverage_rule_id IS NOT NULL AND paid.ready_at IS NULL AND pb.granted_at IS NULL THEN 'AWAITING_AUTHORIZATION'
                WHEN sp.id IS NOT NULL THEN 'IN_PROGRESS' ELSE 'WAITING' END::text AS status
    FROM lab_order lo
    LEFT JOIN invoice_line il ON il.id = lo.invoice_line_id
    LEFT JOIN service_authorization sa ON sa.id = il.authorization_id
    LEFT JOIN LATERAL (
      SELECT bypass.granted_at FROM payment_bypass bypass
      WHERE bypass.encounter_id = lo.encounter_id
        AND (bypass.status = 'ACTIVE' OR
             (sa.authorization_type = 'EMERGENCY_BYPASS' AND sa.reference_id = bypass.id))
      ORDER BY bypass.granted_at LIMIT 1
    ) pb ON TRUE
    LEFT JOIN LATERAL (
      SELECT min(x.created_at) AS ready_at FROM (
        SELECT pa.created_at,
          sum(pa.amount_minor) OVER (ORDER BY pa.created_at, pa.id) AS paid_minor
        FROM payment_allocation pa JOIN payment p ON p.id = pa.payment_id
        WHERE pa.invoice_line_id = il.id AND p.status = 'SETTLED'
      ) x WHERE x.paid_minor >= il.patient_portion_minor
    ) paid ON TRUE
    LEFT JOIN LATERAL (
      SELECT r.id, r.specimen_id, r.entered_at, r.verified_at, r.released_at FROM lab_result r
      WHERE r.lab_order_id = lo.id AND r.amends_id IS NULL
      ORDER BY r.released_at, r.id LIMIT 1
    ) lr ON TRUE
    LEFT JOIN LATERAL (
      SELECT s.id, s.collected_at, s.received_at FROM specimen s
      WHERE s.lab_order_id = lo.id AND s.status <> 'REJECTED'
        AND (lr.id IS NULL OR s.id = lr.specimen_id)
      ORDER BY s.collected_at, s.id LIMIT 1
    ) sp ON TRUE
), pharmacy AS (
    SELECT po.encounter_id, 'PHARMACY'::text AS workflow_type, pl.id AS reference_id,
           po.prescribed_at AS source_at,
           CASE WHEN il.patient_portion_minor = 0 AND il.coverage_rule_id IS NULL THEN po.prescribed_at
                WHEN paid.ready_at IS NOT NULL THEN greatest(po.prescribed_at, paid.ready_at)
                WHEN pb.granted_at IS NOT NULL THEN greatest(po.prescribed_at, pb.granted_at)
                ELSE NULL END AS ready_at,
           te.occurred_at AS start_at,
           CASE WHEN pl.status = 'COMPLETED' THEN dx.dispensed_at ELSE NULL END AS completed_at,
           NULL::timestamptz AS entered_at, NULL::timestamptz AS verified_at,
           NULL::timestamptz AS received_at,
           CASE WHEN pl.status IN ('CANCELLED','UNAVAILABLE') THEN 'CANCELLED'
                WHEN pl.status = 'COMPLETED' THEN 'COMPLETED'
                WHEN il.id IS NULL THEN 'AWAITING_AUTHORIZATION'
                WHEN sa.id IS NULL AND il.patient_portion_minor > 0 AND paid.ready_at IS NULL AND pb.granted_at IS NULL THEN 'AWAITING_PAYMENT'
                WHEN sa.id IS NULL AND il.coverage_rule_id IS NOT NULL AND paid.ready_at IS NULL AND pb.granted_at IS NULL THEN 'AWAITING_AUTHORIZATION'
                WHEN te.id IS NOT NULL OR pl.status = 'PARTIALLY_DISPENSED' THEN 'IN_PROGRESS'
                ELSE 'WAITING' END::text AS status
    FROM prescription_line pl JOIN prescription_order po ON po.id = pl.prescription_order_id
    LEFT JOIN invoice_line il ON il.id = pl.invoice_line_id
    LEFT JOIN service_authorization sa ON sa.id = il.authorization_id
    LEFT JOIN LATERAL (
      SELECT bypass.granted_at FROM payment_bypass bypass
      WHERE bypass.encounter_id = po.encounter_id
        AND (bypass.status = 'ACTIVE' OR
             (sa.authorization_type = 'EMERGENCY_BYPASS' AND sa.reference_id = bypass.id))
      ORDER BY bypass.granted_at LIMIT 1
    ) pb ON TRUE
    LEFT JOIN LATERAL (
      SELECT min(x.created_at) AS ready_at FROM (
        SELECT pa.created_at,
          sum(pa.amount_minor) OVER (ORDER BY pa.created_at, pa.id) AS paid_minor
        FROM payment_allocation pa JOIN payment p ON p.id = pa.payment_id
        WHERE pa.invoice_line_id = il.id AND p.status = 'SETTLED'
      ) x WHERE x.paid_minor >= il.patient_portion_minor
    ) paid ON TRUE
    LEFT JOIN tat_workflow_event te ON te.workflow_type = 'PHARMACY'
      AND te.workflow_reference_id = pl.id AND te.event_type = 'STARTED'
    LEFT JOIN LATERAL (
      SELECT max(d.dispensed_at) AS dispensed_at FROM dispense d WHERE d.prescription_line_id = pl.id
    ) dx ON TRUE
)
SELECT * FROM triage UNION ALL SELECT * FROM doctor UNION ALL SELECT * FROM doctor_wait
UNION ALL SELECT * FROM lab UNION ALL SELECT * FROM pharmacy;
