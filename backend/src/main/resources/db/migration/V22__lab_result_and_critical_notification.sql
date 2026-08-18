-- Phase 4, diagnostics, items 4-5: result entry, release/verification as
-- independent axes, critical alerting. docs/spec/15-lab-technician.md
-- §5-§7, 16-lab-verification-model.md v0.2 §2/§9 (supersedes v0.1 §4-§6;
-- this migration follows v0.2's schema exactly, including its own §9
-- migration sketch, adapted from BIGINT to this repo's UUID keys).
--
-- Release and verification are independent facts about one row, not
-- stages of one status (v0.2 §2): released_at is always set at entry —
-- nothing is ever held back — while verification_state may sit at
-- NOT_REQUIRED or PENDING indefinitely with no queue to clear it, since no
-- role holds LAB_RESULT_VERIFY yet (V5's catalogue grants it to nobody,
-- deliberately). verification_level_applied is snapshotted from
-- lab_test.verification_level at entry — raising a test's level later
-- must not retroactively invalidate results released under the old rule.
CREATE TABLE lab_result (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id                 UUID NOT NULL REFERENCES lab_order(id),
    specimen_id                  UUID NOT NULL REFERENCES specimen(id),

    entered_by                   UUID NOT NULL REFERENCES app_user(id),
    entered_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    verification_state           TEXT NOT NULL DEFAULT 'NOT_REQUIRED'
                                     CHECK (verification_state IN
                                        ('NOT_REQUIRED', 'PENDING', 'VERIFIED', 'AMENDED', 'LAPSED')),
    verified_by                  UUID REFERENCES app_user(id),
    verified_at                  TIMESTAMPTZ,
    verification_comment         TEXT,
    verification_level_applied   TEXT NOT NULL
                                     CHECK (verification_level_applied IN ('NONE', 'SELF', 'SECOND_PERSON', 'SPECIALIST')),
    result_version_hash          TEXT,

    methodology                  TEXT,
    reagents_used_note           TEXT,
    findings                     TEXT,
    interpretation                TEXT,
    reference_range_note         TEXT,
    conclusion                   TEXT,
    -- §5.4: object store key, never a database blob. §4 of the
    -- verification spec: mandatory whenever lab_test.produces_machine_output
    -- is true — enforced in RecordLabResultService (needs the parent
    -- lab_test row the DB alone can't reach without a trigger), not here.
    machine_output_object_key    TEXT,

    amends_id                    UUID REFERENCES lab_result(id),
    amendment_reason             TEXT,

    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- v0.2 §2's constraint, unchanged: self-verification is only valid
    -- when the snapshotted level made it valid at entry time (NONE/SELF).
    CHECK (verified_by IS NULL OR verification_level_applied IN ('NONE', 'SELF') OR verified_by != entered_by),
    CHECK (amends_id IS NULL OR amendment_reason IS NOT NULL)
);

CREATE INDEX idx_lab_result_lab_order ON lab_result (lab_order_id);
CREATE INDEX idx_lab_result_specimen ON lab_result (specimen_id);
CREATE INDEX idx_lab_result_amends ON lab_result (amends_id);

-- §5.1/§5.2: one row per generated parameter field. appliedRangeId
-- snapshots which LabReferenceRange produced the flag — a later retune
-- must not rewrite what was flagged historically.
CREATE TABLE lab_result_parameter_value (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id         UUID NOT NULL REFERENCES lab_result(id),
    parameter_id      UUID NOT NULL REFERENCES lab_test_parameter(id),
    result_value      NUMERIC NOT NULL,
    flag              TEXT NOT NULL CHECK (flag IN ('NORMAL', 'HIGH', 'LOW', 'CRITICAL')),
    flag_overridden   BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason   TEXT,
    applied_range_id  UUID REFERENCES lab_reference_range(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- §5.2: "Override permitted with a reason" — never without one.
    CHECK (NOT flag_overridden OR override_reason IS NOT NULL)
);

CREATE INDEX idx_lab_result_parameter_value_result ON lab_result_parameter_value (result_id);

-- §5.5: reagent lot traceability. stock_batch_id carries no FK — the
-- inventory module doesn't exist yet (root CLAUDE.md "Modules": no
-- cross-module foreign keys except to patient and encounter) — this is a
-- documented hook, nullable, so a result can still be entered before that
-- module exists, same honest-stub treatment as BillingGateImpl's
-- verifiedInsuranceCovers.
CREATE TABLE lab_result_reagent (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id       UUID NOT NULL REFERENCES lab_result(id),
    stock_batch_id  UUID,
    quantity_used   NUMERIC,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lab_result_reagent_result ON lab_result_reagent (result_id);

-- §7: one row per critical parameter on a released result. notified_at is
-- stamped in the same transaction as release — "immediate" here means no
-- delay between release and this row existing, not a separate push step
-- (no notification channel exists yet; this row IS the notification
-- record a future channel would deliver from).
CREATE TABLE critical_result_notification (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id             UUID NOT NULL REFERENCES lab_result(id),
    parameter_id          UUID NOT NULL REFERENCES lab_test_parameter(id),
    value                 NUMERIC NOT NULL,
    notified_user_id      UUID NOT NULL REFERENCES app_user(id),
    notified_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_at       TIMESTAMPTZ,
    acknowledged_by       UUID REFERENCES app_user(id),
    escalated_at          TIMESTAMPTZ,
    escalated_to_user_id  UUID REFERENCES app_user(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_critical_result_notification_result ON critical_result_notification (result_id);
-- What CriticalResultEscalationService's sweep scans: unacknowledged AND
-- not yet escalated. Partial index keeps the sweep cheap regardless of how
-- large the (mostly acknowledged) table grows.
CREATE INDEX idx_critical_result_notification_pending
    ON critical_result_notification (notified_at)
    WHERE acknowledged_at IS NULL AND escalated_at IS NULL;

-- §7 `?Q2`: "15 minutes is typical" — an interim default, not a finding,
-- same status as the duplicate-detection thresholds (backend/CLAUDE.md).
-- Admin-tunable without a redeploy, same as every other protocol_setting.
INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'lab.critical_result.escalation_window_minutes', '15'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;

-- v0.2 §6: drives whether the clinician sees a verification badge at all —
-- no consumer reads this yet (no verification-badge UI/read-model exists
-- in this backend-only slice), seeded now so turning it on later is the
-- "seed row, not a code change" v0.2 §8 describes for the whole feature.
INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'lab.verification_active', 'false'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;
