-- Receptionist + triage input relaxations:
--   1. Triage pain_score is no longer required when has_pain is true; a nurse
--      can log that the patient is in pain without a numeric 0-10 score. Pain
--      location stays required in that case (an unlocated pain report is not
--      actionable).
--   2. Add WIDOWER to marital_status (previously only WIDOWED) so male
--      patients can be recorded distinctly.
--   3. Add FRIEND to next_of_kin.relationship.
--   4. next_of_kin.relationship_other captures the free-text label when
--      relationship = 'OTHER'; required in that case, forbidden otherwise.

-- 1. Triage pain-shape constraint. V11 declared it inline (unnamed), so we
--    look it up in pg_constraint by its definition before dropping.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'triage_assessment'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%has_pain%pain_score IS NOT NULL%pain_location IS NOT NULL%';
    IF cname IS NULL THEN
        RAISE EXCEPTION 'triage_assessment pain-shape CHECK not found — schema drift';
    END IF;
    EXECUTE format('ALTER TABLE triage_assessment DROP CONSTRAINT %I', cname);
END $$;

ALTER TABLE triage_assessment
    ADD CONSTRAINT triage_assessment_pain_shape CHECK (
        (has_pain AND pain_location IS NOT NULL)
        OR (NOT has_pain AND pain_score IS NULL AND pain_location IS NULL)
    );

-- 2. patient.marital_status — add WIDOWER.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'patient'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%marital_status%WIDOWED%';
    IF cname IS NULL THEN
        RAISE EXCEPTION 'patient marital_status CHECK not found — schema drift';
    END IF;
    EXECUTE format('ALTER TABLE patient DROP CONSTRAINT %I', cname);
END $$;

ALTER TABLE patient
    ADD CONSTRAINT patient_marital_status_check CHECK (
        marital_status IS NULL OR marital_status IN (
            'SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'WIDOWER'
        )
    );

-- 3. next_of_kin.relationship — add FRIEND.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'next_of_kin'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%relationship%GUARDIAN%';
    IF cname IS NULL THEN
        RAISE EXCEPTION 'next_of_kin relationship CHECK not found — schema drift';
    END IF;
    EXECUTE format('ALTER TABLE next_of_kin DROP CONSTRAINT %I', cname);
END $$;

ALTER TABLE next_of_kin
    ADD CONSTRAINT next_of_kin_relationship_check CHECK (
        relationship IN ('SPOUSE', 'PARENT', 'CHILD', 'SIBLING', 'GUARDIAN', 'FRIEND', 'OTHER')
    );

-- 4. relationship_other free text — required iff relationship = 'OTHER'.
ALTER TABLE next_of_kin
    ADD COLUMN relationship_other TEXT;

ALTER TABLE next_of_kin
    ADD CONSTRAINT next_of_kin_relationship_other_shape CHECK (
        (relationship = 'OTHER' AND relationship_other IS NOT NULL
                               AND char_length(trim(relationship_other)) > 0)
        OR (relationship <> 'OTHER' AND relationship_other IS NULL)
    );
