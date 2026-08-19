-- Phase 6, item 4: ward care execution, work shifts & roster.
-- 19-work-shifts-roster.md §2/§3.
--
-- No `scheduling` module exists yet in this codebase (it's on
-- backend/CLAUDE.md's aspirational module map but nothing has built it) and
-- this item's only real consumer is ward shift handover, so work_shift and
-- shift_assignment live inside `inpatient` for now — the same
-- self-contained treatment prescription_order/prescription_line got inside
-- `pharmacy` when no `orders` module existed yet (V25). Revisit if/when a
-- real `scheduling` module is built for the doctor end-of-shift prompt,
-- on-call authorisation, and triage staffing consumers §4 describes — none
-- of those are this item's scope.
--
-- This is distinct from the existing inpatient.Shift enum
-- (MORNING/AFTERNOON/NIGHT) and nursing_assignment (V31): that pair answers
-- "which nurse is responsible for THIS admission this shift." work_shift/
-- shift_assignment answer "who is on duty at all," keyed by ward or
-- department, not by admission. Both stay, side by side.
CREATE TABLE work_shift (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              TEXT NOT NULL UNIQUE,
    name              TEXT NOT NULL,
    department_id     UUID REFERENCES department(id),
    start_time        TIME NOT NULL,
    end_time          TIME NOT NULL,
    -- §2 ⚠: not optional, not inferred from end_time < start_time — every
    -- "is this nurse on duty now" calculation breaks silently on a night
    -- shift without this flag being an explicit, stored fact.
    crosses_midnight  BOOLEAN NOT NULL,
    -- Bitmask, Monday = bit 0 .. Sunday = bit 6 — some shifts are
    -- weekday-only. Stored as SMALLINT rather than a text array so
    -- "does this shift run today" is one bitwise AND, not an array scan.
    days_of_week      SMALLINT NOT NULL DEFAULT 127 CHECK (days_of_week BETWEEN 1 AND 127),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_by        UUID NOT NULL REFERENCES app_user(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_shift_department ON work_shift (department_id);

-- §3: shift_date anchors a midnight-crossing shift to its start date — the
-- night shift of 12 July runs 12 July 23:00 to 13 July 07:00 and is filed
-- under 12 July, never split across two rows.
CREATE TABLE shift_assignment (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES app_user(id),
    work_shift_id         UUID NOT NULL REFERENCES work_shift(id),
    shift_date            DATE NOT NULL,
    ward_id               UUID REFERENCES ward(id),
    department_id         UUID REFERENCES department(id),
    role_context          TEXT NOT NULL CHECK (role_context IN ('WARD_NURSE', 'DOCTOR', 'TRIAGE_NURSE', 'ON_CALL_LEAD')),
    status                TEXT NOT NULL DEFAULT 'SCHEDULED'
                              CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'ABSENT', 'SWAPPED')),
    assigned_by           UUID NOT NULL REFERENCES app_user(id),
    assigned_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    swapped_with_user_id  UUID REFERENCES app_user(id),
    swap_reason           TEXT,
    CHECK (status != 'SWAPPED' OR (swapped_with_user_id IS NOT NULL AND swap_reason IS NOT NULL)),
    -- One person can't be rostered twice onto the same shift pattern on the
    -- same date — the real guard, not an application pre-check (root
    -- CLAUDE.md "Concurrency").
    UNIQUE (user_id, work_shift_id, shift_date)
);

CREATE INDEX idx_shift_assignment_ward_date ON shift_assignment (ward_id, shift_date);
CREATE INDEX idx_shift_assignment_department_date ON shift_assignment (department_id, shift_date);
CREATE INDEX idx_shift_assignment_user_date ON shift_assignment (user_id, shift_date);

-- §5/§6: admin-tunable lead times, same ProtocolSettingReader mechanism as
-- every other threshold in this codebase.
INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'shift.end_prompt_lead_minutes', '30'::jsonb, id FROM app_user ORDER BY created_at ASC LIMIT 1;

INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'shift.handover_reminder_lead_minutes', '30'::jsonb, id FROM app_user ORDER BY created_at ASC LIMIT 1;

INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'shift.handover_escalation_minutes', '60'::jsonb, id FROM app_user ORDER BY created_at ASC LIMIT 1;
