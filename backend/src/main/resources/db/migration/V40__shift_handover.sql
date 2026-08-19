-- Phase 6, item 4: ward care execution, shift handover. 17-ward-nurse.md
-- §7. "The highest-risk moment in ward nursing" and absent from the
-- original workflow entirely.
--
-- SBAR structure (situation, background, assessment, recommendation) —
-- standard nurses already know. outstanding_tasks is JSONB: a small,
-- evolving checklist (carried forward until completed or explicitly
-- closed) that doesn't need its own relational shape.
--
-- shift_from/shift_to reference work_shift (V39) rather than free text, so
-- "which shift boundary does this handover belong to" is an actual join,
-- not string matching.
CREATE TABLE shift_handover (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id        UUID NOT NULL REFERENCES admission(id),
    ward_id             UUID NOT NULL REFERENCES ward(id),
    shift_from          UUID NOT NULL REFERENCES work_shift(id),
    shift_to            UUID REFERENCES work_shift(id),
    handover_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    outgoing_nurse_id   UUID NOT NULL REFERENCES app_user(id),
    -- Nullable: the roster identifies who *should* acknowledge, but the
    -- handover is recorded by the outgoing nurse before the incoming nurse
    -- necessarily exists as a distinct actor in this transaction.
    incoming_nurse_id   UUID REFERENCES app_user(id),
    acknowledged_at     TIMESTAMPTZ,
    acknowledged_by     UUID REFERENCES app_user(id),
    situation           TEXT NOT NULL,
    background          TEXT NOT NULL,
    assessment          TEXT NOT NULL,
    recommendation      TEXT NOT NULL,
    outstanding_tasks   JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (acknowledged_at IS NULL OR acknowledged_by IS NOT NULL)
);

CREATE INDEX idx_shift_handover_admission ON shift_handover (admission_id, handover_at DESC);

-- §7: "An unacknowledged handover is flagged" — what the ward dashboard and
-- the escalation sweep scan.
CREATE INDEX idx_shift_handover_unacknowledged
    ON shift_handover (handover_at)
    WHERE acknowledged_at IS NULL;
