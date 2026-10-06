-- Paediatric vital reference bounds — the numbers V9 left NULL because the
-- spec did not supply them yet. Seeded now from the reception→triage sign-off
-- table. Splits the original CHILD (3-12y) band into PRESCHOOL (3-5y) and
-- SCHOOL_AGE (6-12y) so HR and RR differences between preschool and
-- school-age children (which are clinically meaningful) don't get flattened.
--
-- Adult bounds are already seeded by V9 and are untouched here.
--
-- Range versioning follows the price-list convention (see V9 comment): the
-- NULL-bound placeholder rows are closed with effective_to = CURRENT_DATE
-- rather than deleted, and new configured rows opened alongside them. The
-- placeholders have never been evaluated against (the evaluator throws
-- NotConfigured), so this is bookkeeping rather than a real supersede.
--
-- implausible_* bounds are set roughly at physiological reject-outright
-- limits (same order of magnitude as the adult row); abnormal_* bounds
-- carry the "warn on the doctor's screen" numbers.

-- 1. Add PRESCHOOL, SCHOOL_AGE to the age_band CHECK.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'vital_reference_range'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%age_band%'
      AND pg_get_constraintdef(oid) NOT LIKE '%PRESCHOOL%';
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE vital_reference_range DROP CONSTRAINT %I', cname);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'vital_reference_range'::regclass
          AND conname = 'vital_reference_range_age_band_check'
    ) THEN
        ALTER TABLE vital_reference_range
            ADD CONSTRAINT vital_reference_range_age_band_check CHECK (
                age_band IN ('NEONATE', 'INFANT', 'TODDLER', 'CHILD',
                             'PRESCHOOL', 'SCHOOL_AGE', 'ADOLESCENT',
                             'ADULT', 'ELDERLY')
            );
    END IF;
END $$;

-- 2. Close the V9 placeholder rows (NULL bounds) so we can open configured
--    replacements. CHILD is retired: no forAge branch returns it any more.
UPDATE vital_reference_range
   SET effective_to = CURRENT_DATE
 WHERE effective_to IS NULL
   AND age_band IN ('NEONATE', 'INFANT', 'TODDLER', 'CHILD', 'ADOLESCENT')
   AND implausible_low IS NULL AND implausible_high IS NULL
   AND abnormal_low IS NULL AND abnormal_high IS NULL;

-- 3. Seed configured rows.
--    Sourced from the age-band table supplied at commit time; SpO2 rows
--    use the user-supplied 95% floor. Temperature bounds are copied from
--    the adult row (physiological temp thresholds do not shift materially
--    across age).
INSERT INTO vital_reference_range (
    vital_type, age_band, sex,
    implausible_low, implausible_high, abnormal_low, abnormal_high,
    created_by
)
SELECT v.vital_type, v.age_band, 'ANY',
       v.implausible_low, v.implausible_high, v.abnormal_low, v.abnormal_high,
       (SELECT id FROM app_user ORDER BY created_at ASC LIMIT 1)
FROM (VALUES
    -- Newborn (0-28d) → NEONATE
    ('TEMPERATURE',      'NEONATE',     30::numeric, 43::numeric,  35.5::numeric, 38.0::numeric),
    ('PULSE',            'NEONATE',     20,          250,          100,           160),
    ('RESPIRATORY_RATE', 'NEONATE',     4,           100,          40,            60),
    ('SYSTOLIC_BP',      'NEONATE',     30,          200,          60,            80),
    ('DIASTOLIC_BP',     'NEONATE',     15,          150,          30,            50),
    ('SPO2',             'NEONATE',     50,          100,          95,            NULL),

    -- Infant (1-12m) → INFANT
    ('TEMPERATURE',      'INFANT',      30,          43,           35.5,          38.0),
    ('PULSE',            'INFANT',      20,          250,          100,           160),
    ('RESPIRATORY_RATE', 'INFANT',      4,           100,          30,            50),
    ('SYSTOLIC_BP',      'INFANT',      40,          200,          70,            100),
    ('DIASTOLIC_BP',     'INFANT',      15,          150,          50,            65),
    ('SPO2',             'INFANT',      50,          100,          95,            NULL),

    -- Toddler (1-3y) → TODDLER
    ('TEMPERATURE',      'TODDLER',     30,          43,           35.5,          38.0),
    ('PULSE',            'TODDLER',     20,          250,          90,            150),
    ('RESPIRATORY_RATE', 'TODDLER',     4,           80,           24,            40),
    ('SYSTOLIC_BP',      'TODDLER',     40,          250,          80,            110),
    ('DIASTOLIC_BP',     'TODDLER',     20,          180,          50,            80),
    ('SPO2',             'TODDLER',     50,          100,          95,            NULL),

    -- Preschool (3-5y) → PRESCHOOL
    ('TEMPERATURE',      'PRESCHOOL',   30,          43,           35.5,          38.0),
    ('PULSE',            'PRESCHOOL',   20,          250,          80,            140),
    ('RESPIRATORY_RATE', 'PRESCHOOL',   4,           80,           22,            34),
    ('SYSTOLIC_BP',      'PRESCHOOL',   40,          250,          85,            115),
    ('DIASTOLIC_BP',     'PRESCHOOL',   20,          180,          55,            80),
    ('SPO2',             'PRESCHOOL',   50,          100,          95,            NULL),

    -- School-age (6-12y) → SCHOOL_AGE
    ('TEMPERATURE',      'SCHOOL_AGE',  30,          43,           35.5,          38.0),
    ('PULSE',            'SCHOOL_AGE',  20,          250,          70,            120),
    ('RESPIRATORY_RATE', 'SCHOOL_AGE',  4,           80,           18,            30),
    ('SYSTOLIC_BP',      'SCHOOL_AGE',  40,          300,          90,            120),
    ('DIASTOLIC_BP',     'SCHOOL_AGE',  20,          200,          60,            80),
    ('SPO2',             'SCHOOL_AGE',  50,          100,          95,            NULL),

    -- Adolescent (13-17y) → ADOLESCENT
    ('TEMPERATURE',      'ADOLESCENT',  30,          43,           35.5,          38.0),
    ('PULSE',            'ADOLESCENT',  20,          250,          60,            100),
    ('RESPIRATORY_RATE', 'ADOLESCENT',  4,           80,           12,            20),
    ('SYSTOLIC_BP',      'ADOLESCENT',  40,          300,          100,           120),
    ('DIASTOLIC_BP',     'ADOLESCENT',  20,          200,          60,            80),
    ('SPO2',             'ADOLESCENT',  50,          100,          95,            NULL)
) AS v(vital_type, age_band, implausible_low, implausible_high, abnormal_low, abnormal_high)
WHERE NOT EXISTS (
    SELECT 1 FROM vital_reference_range r
     WHERE r.vital_type = v.vital_type
       AND r.age_band   = v.age_band
       AND r.sex        = 'ANY'
       AND r.effective_to IS NULL
);
