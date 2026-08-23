-- 05a-receptionist-decisions.md D1/§3: forward booking is out of scope for
-- v1 — the only creation path is a doctor booking a follow-up after a
-- consultation. root CLAUDE.md's module list already reserves "scheduling"
-- for this; this is that module's first migration.
CREATE TABLE appointment (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id             UUID NOT NULL REFERENCES patient(id),
    provider_id            UUID NOT NULL REFERENCES app_user(id),
    -- The consultation encounter that generated this follow-up — every
    -- follow-up in v1 comes from a doctor finishing a consultation (D1).
    source_encounter_id    UUID NOT NULL REFERENCES encounter(id),
    scheduled_at           TIMESTAMPTZ NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'SCHEDULED'
                               CHECK (status IN ('SCHEDULED', 'ATTENDED', 'CANCELLED', 'NO_SHOW')),
    -- Set on arrival (05a §3.3) — the new encounter the follow-up turned
    -- into. Null until then.
    attended_encounter_id  UUID REFERENCES encounter(id),
    notes                  TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID NOT NULL REFERENCES app_user(id)
);

CREATE INDEX idx_appointment_patient ON appointment(patient_id);
CREATE INDEX idx_appointment_scheduled_at ON appointment(scheduled_at);
-- Expected Returns (05a §3.2) and the dashboard's upcoming panel both
-- query "still SCHEDULED" — the common case, worth its own partial index.
CREATE INDEX idx_appointment_scheduled_pending ON appointment(scheduled_at) WHERE status = 'SCHEDULED';
