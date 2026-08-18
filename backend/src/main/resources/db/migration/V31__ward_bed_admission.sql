-- Phase 6, item 1: ward/bed inventory, admission, bed assignment, and the
-- per-shift nursing roster. 17-ward-nurse.md §8, 07-doctor.md §7 (ADMITTED
-- disposition), 04-procedure-bedday-rules.md §3.4 (ward_class / "private
-- wins the bed-day rate").
--
-- `ward` already exists — V1__foundation.sql created it early because
-- shared/authz's WARD scope-hierarchy walk (JdbcScopeHierarchyPort) needed
-- a table to resolve WARD -> DEPARTMENT against before any module used it.
-- Brought in line with 17-ward-nurse.md §8.1 here rather than duplicated:
-- facility_id (root CLAUDE.md "Facility" — a ward is a place),
-- daily_rate_minor and ward_class (the admitting doctor sets this on the
-- Admission, not the nurse — §8.1's own ⚠). No writer has ever inserted a
-- ward row (grep confirms), so the new NOT NULL columns are added directly.
-- bed_count is dropped: a stored counter the real Bed rows below make
-- redundant and would silently drift from (root CLAUDE.md "Data" —
-- balances are derived, never a mutable counter; same reasoning as
-- inventory's StockBalance being computed from StockMovement, never typed).

ALTER TABLE ward
    ADD COLUMN facility_id      UUID REFERENCES facility(id),
    ADD COLUMN daily_rate_minor BIGINT,
    ADD COLUMN currency_code    TEXT NOT NULL DEFAULT 'TZS',
    ADD COLUMN ward_class       TEXT;

UPDATE ward SET facility_id = (SELECT facility_id FROM department WHERE department.id = ward.department_id)
 WHERE facility_id IS NULL;

ALTER TABLE ward
    ALTER COLUMN facility_id SET NOT NULL,
    ADD CONSTRAINT ward_daily_rate_minor_not_null CHECK (daily_rate_minor IS NOT NULL),
    ADD CONSTRAINT ward_daily_rate_minor_check CHECK (daily_rate_minor >= 0),
    ADD CONSTRAINT ward_class_check CHECK (ward_class IN ('GENERAL', 'PRIVATE')),
    ADD CONSTRAINT ward_class_not_null CHECK (ward_class IS NOT NULL),
    ADD CONSTRAINT ward_gender_restriction_check CHECK (gender_restriction IN ('MALE', 'FEMALE')),
    DROP COLUMN bed_count;

CREATE INDEX idx_ward_facility ON ward(facility_id);

-- 17-ward-nurse.md §8.1: "Beds are rows, not free text." bed_type is
-- free-text (an "Adult standard" / "Paediatric cot" style label) — the spec
-- gives no fixed enumeration to CHECK against, unlike ward_class.
CREATE TABLE bed (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ward_id     UUID NOT NULL REFERENCES ward(id),
    label       TEXT NOT NULL,
    bed_type    TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'OUT_OF_SERVICE')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (ward_id, label)
);

CREATE INDEX idx_bed_ward ON bed(ward_id);

-- 07-doctor.md §7's ADMITTED disposition + §11: attaches to the encounter
-- that produced it (never a fresh encounter — the one-open-encounter index
-- already guards one admission path per patient). ordered_ward_class is
-- the doctor's order; the nurse only ever picks a bed within it (§8.1 ⚠).
CREATE TABLE admission (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id          UUID NOT NULL REFERENCES encounter(id),
    patient_id            UUID NOT NULL REFERENCES patient(id),
    facility_id           UUID NOT NULL REFERENCES facility(id),
    admitting_doctor_id   UUID NOT NULL REFERENCES app_user(id),
    ordered_ward_class    TEXT NOT NULL CHECK (ordered_ward_class IN ('GENERAL', 'PRIVATE')),
    admitted_at           TIMESTAMPTZ NOT NULL,
    -- 04-procedure-bedday-rules.md §3.6: two timestamps, not one.
    -- discharge_ordered_at stops billing; actual_departure_at (this
    -- admission's own field, distinct from any one BedAssignment's
    -- released_at — a transfer can release an earlier bed well before the
    -- patient actually leaves) is when the bed-day algorithm's gap and the
    -- clock actually stop. Both null until step 3.
    discharge_ordered_at  TIMESTAMPTZ,
    discharge_ordered_by  UUID REFERENCES app_user(id),
    actual_departure_at   TIMESTAMPTZ,
    version               INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One Admission per encounter — a double-submitted admit request must not
-- create two. (The encounter's own one-open-per-patient index is the guard
-- against two concurrent admissions for the same patient; this is narrower,
-- guarding the encounter -> admission edge specifically.)
CREATE UNIQUE INDEX idx_admission_one_per_encounter ON admission(encounter_id);
CREATE INDEX idx_admission_patient ON admission(patient_id);
CREATE INDEX idx_admission_facility ON admission(facility_id);

-- root CLAUDE.md "Concurrency": UNIQUE (bed_id) WHERE released_at IS NULL —
-- the whole protection against two nurses assigning the same bed. A
-- read-then-write check in application code loses the race; this partial
-- unique index is the actual guard, not the service's pre-check.
CREATE TABLE bed_assignment (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id                UUID NOT NULL REFERENCES admission(id),
    bed_id                      UUID NOT NULL REFERENCES bed(id),
    assigned_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at                 TIMESTAMPTZ,
    assigned_by                 UUID NOT NULL REFERENCES app_user(id),
    -- 17-ward-nurse.md §1 ⚠: placing a patient above their ordered class
    -- wins the higher bed-day rate and needs a recorded reason, because the
    -- patient finds out at the desk otherwise.
    placed_above_ordered_class  BOOLEAN NOT NULL DEFAULT FALSE,
    placement_reason            TEXT CHECK (placement_reason IN (
                                     'NO_BED_IN_ORDERED_CLASS', 'CLINICAL_NEED', 'PATIENT_REQUEST', 'OTHER')),
    CONSTRAINT bed_assignment_placement_reason_required
        CHECK (NOT placed_above_ordered_class OR placement_reason IS NOT NULL)
);

CREATE UNIQUE INDEX idx_bed_assignment_one_active_per_bed ON bed_assignment(bed_id) WHERE released_at IS NULL;
CREATE INDEX idx_bed_assignment_admission ON bed_assignment(admission_id);

-- 17-ward-nurse.md §8.2 ⚠: a single assignedNurse field cannot survive a
-- multi-day stay across three shifts a day. Care responsibility comes from
-- this roster, not a mutable field on the admission.
CREATE TABLE nursing_assignment (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id     UUID NOT NULL REFERENCES admission(id),
    nurse_id         UUID NOT NULL REFERENCES app_user(id),
    shift            TEXT NOT NULL CHECK (shift IN ('MORNING', 'AFTERNOON', 'NIGHT')),
    assignment_date  DATE NOT NULL,
    assigned_by      UUID NOT NULL REFERENCES app_user(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (admission_id, nurse_id, shift, assignment_date)
);

CREATE INDEX idx_nursing_assignment_admission ON nursing_assignment(admission_id);
CREATE INDEX idx_nursing_assignment_nurse_date ON nursing_assignment(nurse_id, assignment_date);
