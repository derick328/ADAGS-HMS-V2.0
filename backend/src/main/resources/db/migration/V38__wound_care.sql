-- Phase 6, item 4: ward care execution, wound care. 17-ward-nurse.md §6.
-- Fields as described in the spec — no changes needed there. The one
-- addition is behavioural (signs_of_infection = true notifies the covering
-- doctor via ward_alert_notification), not a schema change.
CREATE TABLE wound_care (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id           UUID NOT NULL REFERENCES admission(id),
    appearance             TEXT NOT NULL,
    dressing_changed       BOOLEAN NOT NULL,
    dressing_type          TEXT,
    signs_of_infection     BOOLEAN NOT NULL DEFAULT FALSE,
    infection_description  TEXT,
    remarks                TEXT,
    recorded_by            UUID NOT NULL REFERENCES app_user(id),
    recorded_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (NOT signs_of_infection OR infection_description IS NOT NULL)
);

CREATE INDEX idx_wound_care_admission ON wound_care (admission_id, recorded_at DESC);
