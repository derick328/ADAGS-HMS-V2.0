-- Phase 1, front office, item 2: the encounter lifecycle.
-- 01-architecture.md §2 (state machine as the central aggregate — its state
-- names are superseded by 05a-receptionist-decisions.md §2, the "final for
-- v1" machine; see backend/CLAUDE.md), 05-receptionist.md §5,
-- 02-billing-architecture.md §2 and §8.
--
-- Encounter carries facility_id; Patient does not (root CLAUDE.md
-- "Facility") — an encounter happened at a place, a patient is a person.

CREATE TABLE encounter (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id     UUID NOT NULL REFERENCES patient(id),
    facility_id    UUID NOT NULL REFERENCES facility(id),
    department_id  UUID NOT NULL REFERENCES department(id),
    -- 05-receptionist.md ⚠7: arrival against a booked appointment links the
    -- encounter and marks the appointment ATTENDED — that link is the
    -- no-show metric. No FK yet: the scheduling module and its appointment
    -- table don't exist. The column is added now anyway, because adding it
    -- after scheduling lands would mean every encounter opened before then
    -- has no link and never can — the information is gone by then, not
    -- merely unpopulated.
    appointment_id UUID,
    -- 06-triage.md §5: "Confirmed: referral can never be blank" — but it
    -- doesn't exist until triage assigns it, so nullable here and enforced
    -- as required at the triage submission service, not the schema.
    assigned_provider_id UUID REFERENCES app_user(id),
    type           TEXT NOT NULL CHECK (type IN ('OUTPATIENT', 'EMERGENCY')),
    state          TEXT NOT NULL CHECK (state IN (
                       'AWAITING_TRIAGE', 'AWAITING_PAYMENT', 'AWAITING_CONSULT', 'IN_CONSULT',
                       'AWAITING_RESULTS', 'CONSULT_COMPLETE', 'PENDING_SETTLEMENT',
                       'ADMITTED', 'IN_WARD', 'DISCHARGE_ORDERED',
                       'LEFT_WITHOUT_BEING_SEEN', 'ABANDONED_UNPAID', 'CANCELLED_IN_ERROR',
                       'REFERRED_OUT', 'DECEASED', 'CLOSED', 'DISCHARGED'
                   )),
    payer_type     TEXT NOT NULL CHECK (payer_type IN ('CASH', 'INSURANCE')),
    -- backend/CLAUDE.md "Optimistic locking (@Version) on Encounter..." —
    -- plain JDBC here rather than JPA's @Version, so the increment and the
    -- WHERE version = :expected guard are explicit in
    -- JdbcEncounterRepository rather than framework-managed. Bumped on
    -- every write to this row, not only the triage submission path, so it
    -- stays meaningful as a lock for any future concurrent writer.
    version        INTEGER NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID NOT NULL REFERENCES app_user(id)
);

-- root CLAUDE.md "Concurrency": UNIQUE (patient_id) WHERE encounter state
-- not terminal. A read-then-write "does this patient have an open encounter"
-- check in application code loses the race — this is the actual guard;
-- OpenEncounterService's pre-check is just there to avoid hitting it on the
-- common path.
CREATE UNIQUE INDEX idx_encounter_one_open_per_patient ON encounter (patient_id)
    WHERE state NOT IN (
        'LEFT_WITHOUT_BEING_SEEN', 'ABANDONED_UNPAID', 'CANCELLED_IN_ERROR',
        'REFERRED_OUT', 'DECEASED', 'CLOSED', 'DISCHARGED'
    );

CREATE INDEX idx_encounter_patient ON encounter (patient_id);
CREATE INDEX idx_encounter_facility_state ON encounter (facility_id, state);

-- 01-architecture.md §2.1 rule 4: every transition writes a row here —
-- audit trail, queue-time analytics, and debugging tool in one table.
-- Written inside the same transaction as the state change it records
-- (OpenEncounterService, CloseEncounterService); if this insert fails, the
-- transition rolls back with it.
CREATE TABLE encounter_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id   UUID NOT NULL REFERENCES encounter(id),
    -- NULL only for the first event on an encounter (05-receptionist.md
    -- §5.2 step 4: "EncounterEvent(null -> AWAITING_TRIAGE, ...)").
    from_state     TEXT CHECK (from_state IN (
                       'AWAITING_TRIAGE', 'AWAITING_PAYMENT', 'AWAITING_CONSULT', 'IN_CONSULT',
                       'AWAITING_RESULTS', 'CONSULT_COMPLETE', 'PENDING_SETTLEMENT',
                       'ADMITTED', 'IN_WARD', 'DISCHARGE_ORDERED',
                       'LEFT_WITHOUT_BEING_SEEN', 'ABANDONED_UNPAID', 'CANCELLED_IN_ERROR',
                       'REFERRED_OUT', 'DECEASED', 'CLOSED', 'DISCHARGED'
                   )),
    to_state       TEXT NOT NULL CHECK (to_state IN (
                       'AWAITING_TRIAGE', 'AWAITING_PAYMENT', 'AWAITING_CONSULT', 'IN_CONSULT',
                       'AWAITING_RESULTS', 'CONSULT_COMPLETE', 'PENDING_SETTLEMENT',
                       'ADMITTED', 'IN_WARD', 'DISCHARGE_ORDERED',
                       'LEFT_WITHOUT_BEING_SEEN', 'ABANDONED_UNPAID', 'CANCELLED_IN_ERROR',
                       'REFERRED_OUT', 'DECEASED', 'CLOSED', 'DISCHARGED'
                   )),
    actor_user_id  UUID NOT NULL REFERENCES app_user(id),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason         TEXT
);

CREATE INDEX idx_encounter_event_encounter ON encounter_event (encounter_id, occurred_at);
