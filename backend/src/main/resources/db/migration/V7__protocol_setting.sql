-- ProtocolSetting (docs/spec/11-clinical-lead-role.md §6), referenced across
-- several later-phase specs (09-doctor-incomplete-consultations.md,
-- 16-lab-verification-model.md, 18-admin.md §12, 19-work-shifts-roster.md)
-- as the generic, admin-tunable key/value store for thresholds that must
-- change without a redeploy. First consumer: 05-receptionist.md §4.1's
-- duplicate-detection similarity thresholds.
--
-- department_id is nullable: a row with NULL department is a system-wide
-- default. Patient registration is not department-scoped (root CLAUDE.md
-- "Facility": patient carries no facility_id, let alone a department one),
-- so both seed rows below are system-wide.

CREATE TABLE protocol_setting (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id  UUID REFERENCES department(id),
    setting_key    TEXT NOT NULL,
    setting_value  JSONB NOT NULL,
    updated_by     UUID NOT NULL REFERENCES app_user(id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    previous_value JSONB
);

-- NULL department_id means "system-wide". A plain UNIQUE(department_id,
-- setting_key) would not actually enforce one-row-per-key at that scope —
-- Postgres treats every NULL as distinct, so duplicate system-wide rows for
-- the same key would pass silently. Partial indexes make each scope a real
-- constraint (root CLAUDE.md "Concurrency").
CREATE UNIQUE INDEX idx_protocol_setting_global_key ON protocol_setting (setting_key)
    WHERE department_id IS NULL;
CREATE UNIQUE INDEX idx_protocol_setting_department_key ON protocol_setting (department_id, setting_key)
    WHERE department_id IS NOT NULL;

-- ⚠ 11-clinical-lead-role.md §6: "what was the protocol when this patient
-- was triaged" must be answerable. A trigger, not application code, so a
-- raw UPDATE can't silently skip recording what the value used to be.
CREATE FUNCTION fn_protocol_setting_track_previous_value() RETURNS TRIGGER AS $$
BEGIN
    NEW.previous_value := OLD.setting_value;
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protocol_setting_track_previous_value
    BEFORE UPDATE OF setting_value ON protocol_setting
    FOR EACH ROW
    WHEN (NEW.setting_value IS DISTINCT FROM OLD.setting_value)
    EXECUTE FUNCTION fn_protocol_setting_track_previous_value();

-- Seed system-wide defaults for the duplicate-detection thresholds. These
-- values are a documented starting point (see backend/CLAUDE.md for the
-- similarity-score evidence behind them), not a final calibration —
-- 05-receptionist.md §4.1 directs tuning this against real data once the
-- system is live, which is the entire reason this lives in a table an admin
-- can update instead of a constant that needs a deploy.
INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'patients.duplicate_detection.low_similarity_threshold', '0.4'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;

INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'patients.duplicate_detection.high_similarity_threshold', '0.65'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;
