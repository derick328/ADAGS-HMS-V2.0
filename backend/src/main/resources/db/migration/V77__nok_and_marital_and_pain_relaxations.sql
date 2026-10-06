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
--
-- Each block is written to be safe if a prior partial run already applied
-- some of the changes: the DO blocks skip DROP if no matching constraint is
-- found, and every ADD CONSTRAINT is guarded by an existence check on the
-- target constraint name.

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
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE triage_assessment DROP CONSTRAINT %I', cname);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'triage_assessment'::regclass
          AND conname = 'triage_assessment_pain_shape'
    ) THEN
        ALTER TABLE triage_assessment
            ADD CONSTRAINT triage_assessment_pain_shape CHECK (
                (has_pain AND pain_location IS NOT NULL)
                OR (NOT has_pain AND pain_score IS NULL AND pain_location IS NULL)
            );
    END IF;
END $$;

-- 2. patient.marital_status — add WIDOWER.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'patient'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%marital_status%WIDOWED%'
      AND pg_get_constraintdef(oid) NOT LIKE '%WIDOWER%';
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE patient DROP CONSTRAINT %I', cname);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'patient'::regclass
          AND conname = 'patient_marital_status_check'
    ) THEN
        ALTER TABLE patient
            ADD CONSTRAINT patient_marital_status_check CHECK (
                marital_status IS NULL OR marital_status IN (
                    'SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'WIDOWER'
                )
            );
    END IF;
END $$;

-- 3. next_of_kin.relationship — add FRIEND.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'next_of_kin'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%relationship%GUARDIAN%'
      AND pg_get_constraintdef(oid) NOT LIKE '%FRIEND%';
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE next_of_kin DROP CONSTRAINT %I', cname);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'next_of_kin'::regclass
          AND conname = 'next_of_kin_relationship_check'
    ) THEN
        ALTER TABLE next_of_kin
            ADD CONSTRAINT next_of_kin_relationship_check CHECK (
                relationship IN ('SPOUSE', 'PARENT', 'CHILD', 'SIBLING', 'GUARDIAN', 'FRIEND', 'OTHER')
            );
    END IF;
END $$;

-- 4. relationship_other free text — required iff relationship = 'OTHER'.
ALTER TABLE next_of_kin
    ADD COLUMN IF NOT EXISTS relationship_other TEXT;

-- Pre-existing rows with relationship='OTHER' predate this field — no free
-- text was ever captured for them, so we backfill a placeholder. Live data
-- will have populated the column at insert time going forward.
UPDATE next_of_kin
   SET relationship_other = 'unspecified'
 WHERE relationship = 'OTHER'
   AND (relationship_other IS NULL OR trim(relationship_other) = '');

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'next_of_kin'::regclass
          AND conname = 'next_of_kin_relationship_other_shape'
    ) THEN
        ALTER TABLE next_of_kin
            ADD CONSTRAINT next_of_kin_relationship_other_shape CHECK (
                (relationship = 'OTHER' AND relationship_other IS NOT NULL
                                       AND char_length(trim(relationship_other)) > 0)
                OR (relationship <> 'OTHER' AND relationship_other IS NULL)
            );
    END IF;
END $$;
