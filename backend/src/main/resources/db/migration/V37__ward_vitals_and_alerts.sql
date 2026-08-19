-- Phase 6, item 4: ward care execution, vitals + alerting. 17-ward-nurse.md
-- §4.1/§4.3/§4.4.
--
-- pain_score/intake_ml/output_ml onto the EXISTING vital_signs table
-- (V10) — §4.1 names three separate vitals tables as a bug; ward rounds
-- vitals are context = WARD_ROUNDS rows in this same table, not a new one.
-- Nullable: triage/consultation/post_op readings don't carry fluid balance,
-- and every existing row predates these columns.
ALTER TABLE vital_signs
    ADD COLUMN pain_score INTEGER CHECK (pain_score IS NULL OR pain_score BETWEEN 0 AND 10),
    ADD COLUMN intake_ml  INTEGER CHECK (intake_ml IS NULL OR intake_ml >= 0),
    ADD COLUMN output_ml  INTEGER CHECK (output_ml IS NULL OR output_ml >= 0);

-- §4.3: "hasAlerts is set and nothing follows... a flag nobody is told
-- about is a record, not an alert." Same shape as
-- laboratory.critical_result_notification (V22) — replicated rather than
-- reused because a cross-module FK to a laboratory table is not allowed
-- (root CLAUDE.md "Modules") and ClinicalLeadDirectory is not
-- facade-exposed. source_id is polymorphic (a vital_signs.id or a
-- wound_care.id depending on source_type), so it carries no FK, same
-- reasoning vital_signs.context_ref_id already documents.
CREATE TABLE ward_alert_notification (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id          UUID NOT NULL REFERENCES admission(id),
    source_type           TEXT NOT NULL CHECK (source_type IN ('VITAL_BREACH', 'WOUND_INFECTION', 'DRUG_UNAVAILABLE')),
    source_id             UUID NOT NULL,
    detail                TEXT NOT NULL,
    notified_user_id      UUID NOT NULL REFERENCES app_user(id),
    notified_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_at       TIMESTAMPTZ,
    acknowledged_by       UUID REFERENCES app_user(id),
    escalated_at          TIMESTAMPTZ,
    escalated_to_user_id  UUID REFERENCES app_user(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ward_alert_notification_admission ON ward_alert_notification (admission_id);

-- What WardAlertEscalationService's sweep scans — same partial-index shape
-- as idx_critical_result_notification_pending.
CREATE INDEX idx_ward_alert_notification_pending
    ON ward_alert_notification (notified_at)
    WHERE acknowledged_at IS NULL AND escalated_at IS NULL;

-- §4.3 `?Q1`: "probably shorter than the lab's" — left configurable rather
-- than guessed, same 15-minute interim default as
-- lab.critical_result.escalation_window_minutes (V22).
INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'ward.alert.escalation_window_minutes', '15'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;
