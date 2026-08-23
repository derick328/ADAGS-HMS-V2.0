-- Phase 8, surgery module, item 4: surgical safety checklist.
-- 04-*.md §7, root CLAUDE.md task text.
--
-- Insert-only, no UNIQUE(surgery_order_id, phase): a failed SIGN_OUT count
-- must be resolvable by recording a further attempt, not blocked forever
-- by a one-row-per-phase constraint. Gate checks (item 4/item 5's
-- StartSurgeryOrderService, item 3's CompleteSurgeryOrderService) ask
-- "does a SATISFYING row exist for this phase," not "does a row exist" —
-- so an earlier failed SIGN_OUT attempt never blocks a later successful one.
--
-- count_correct only applies to SIGN_OUT (the swab/instrument count) —
-- NULL for SIGN_IN/TIME_OUT, required (TRUE or FALSE, never unset) for
-- SIGN_OUT. "count_correct is NEVER waivable" (task text) — there is no
-- column or flag anywhere that lets a FALSE count be overridden; the
-- only way SIGN_OUT is satisfied is a row with count_correct = TRUE.
--
-- is_emergency_reduced = TRUE only for SIGN_IN/TIME_OUT when the order's
-- urgency is IMMEDIATE (checked at the service layer — needs the order
-- row, not expressible as a bare CHECK); "SIGN_OUT is never reduced" is
-- expressible and is a CHECK.
CREATE TABLE surgical_checklist (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    surgery_order_id         UUID NOT NULL REFERENCES surgery_order(id),
    phase                    TEXT NOT NULL CHECK (phase IN ('SIGN_IN', 'TIME_OUT', 'SIGN_OUT')),
    items                    JSONB NOT NULL DEFAULT '[]'::jsonb,
    completed_by             UUID NOT NULL REFERENCES app_user(id),
    completed_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    count_correct            BOOLEAN,
    count_discrepancy_note   TEXT,
    is_emergency_reduced     BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (phase = 'SIGN_OUT' OR count_correct IS NULL),
    CHECK (phase != 'SIGN_OUT' OR count_correct IS NOT NULL),
    -- Same wastage_reason idiom as V51: count_correct = FALSE requires a note.
    CHECK (count_correct IS NOT FALSE OR count_discrepancy_note IS NOT NULL),
    CHECK (phase != 'SIGN_OUT' OR is_emergency_reduced = FALSE)
);

CREATE INDEX idx_surgical_checklist_order_phase ON surgical_checklist (surgery_order_id, phase);
