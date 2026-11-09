-- Clinician answers are append-only so earlier answers remain auditable.
CREATE TABLE consultation_pregnancy_assessment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id UUID NOT NULL REFERENCES encounter(id),
    status TEXT NOT NULL CHECK (status IN ('YES', 'NO', 'UNKNOWN')),
    recorded_by UUID NOT NULL REFERENCES app_user(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_consultation_pregnancy_assessment_latest
    ON consultation_pregnancy_assessment (encounter_id, recorded_at DESC, id DESC);
