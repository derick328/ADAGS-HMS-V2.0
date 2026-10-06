-- V77 relaxed pain_score so a triage row with has_pain=true can leave the
-- 0-10 score blank. This does the same for fall risk: fall_score becomes
-- optional even when fall_risk is true. NOT fall_risk still forbids a
-- fall_score value (a score with no risk flag is a data-entry mistake).
--
-- Priority stays NOT NULL — the frontend defaults a blank selection to
-- NON_URGENT at submit time so the field is optional in the UI without
-- turning null priority into a queue-ordering / IMMEDIATE-bypass edge case.

DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'triage_assessment'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%fall_risk%fall_score IS NOT NULL%';
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE triage_assessment DROP CONSTRAINT %I', cname);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'triage_assessment'::regclass
          AND conname = 'triage_assessment_fall_shape'
    ) THEN
        ALTER TABLE triage_assessment
            ADD CONSTRAINT triage_assessment_fall_shape CHECK (
                fall_risk OR fall_score IS NULL
            );
    END IF;
END $$;
