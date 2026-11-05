-- Removes the newest duplicate registration, retaining the patient kept by
-- keep_latest_derick_reset.sql. Existing clinical/billing foreign keys remain
-- enabled. Untriaged test encounters, their events, and their drafts are
-- removed with the duplicate; other clinical/billing links stop the reset.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
LOCK TABLE patient IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE derick_to_delete ON COMMIT DROP AS
SELECT id, hospital_no
FROM patient
WHERE lower(regexp_replace(
        btrim(concat_ws(' ', given_name, middle_name, family_name)),
        '[[:space:]]+', ' ', 'g')) = 'derick gabriel mhidze'
  AND merged_into_id IS NULL
ORDER BY created_at DESC, id DESC
LIMIT 1;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM derick_to_delete) THEN
        RAISE EXCEPTION 'No matching Derick record found. Nothing deleted.';
    END IF;
    IF EXISTS (SELECT 1 FROM derick_to_delete
               WHERE id = 'c3babc44-f0bd-4561-b707-f46b75ff33b7') THEN
        RAISE EXCEPTION 'The newest record is the original retained patient. Nothing deleted.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM patient
                   WHERE id = 'c3babc44-f0bd-4561-b707-f46b75ff33b7') THEN
        RAISE EXCEPTION 'The original retained patient is missing. Nothing deleted.';
    END IF;
    -- Prevent a triage submission from changing a visit during this cleanup.
    PERFORM id FROM encounter
    WHERE patient_id IN (SELECT id FROM derick_to_delete)
    FOR UPDATE;

    IF EXISTS (SELECT 1 FROM invoice
               WHERE patient_id IN (SELECT id FROM derick_to_delete)
                  OR encounter_id IN (SELECT id FROM encounter
                                      WHERE patient_id IN (SELECT id FROM derick_to_delete))) THEN
        RAISE EXCEPTION 'The newest duplicate has an invoice. Nothing deleted; invoice cleanup needs its linked billing records inspected.';
    END IF;
    IF EXISTS (SELECT 1 FROM encounter
               WHERE patient_id IN (SELECT id FROM derick_to_delete)
                 AND state <> 'AWAITING_TRIAGE') THEN
        RAISE EXCEPTION 'The newest duplicate has visits beyond awaiting triage (%). Nothing deleted.',
            (SELECT string_agg(DISTINCT state, ', ') FROM encounter
             WHERE patient_id IN (SELECT id FROM derick_to_delete));
    END IF;
END $$;

DELETE FROM triage_assessment_draft
WHERE encounter_id IN (SELECT id FROM encounter
                       WHERE patient_id IN (SELECT id FROM derick_to_delete));
DELETE FROM encounter_event
WHERE encounter_id IN (SELECT id FROM encounter
                       WHERE patient_id IN (SELECT id FROM derick_to_delete));
DELETE FROM encounter WHERE patient_id IN (SELECT id FROM derick_to_delete);

DELETE FROM patient_identifier WHERE patient_id IN (SELECT id FROM derick_to_delete);
DELETE FROM next_of_kin WHERE patient_id IN (SELECT id FROM derick_to_delete);
DELETE FROM patient_address WHERE patient_id IN (SELECT id FROM derick_to_delete);
DELETE FROM insurance_policy WHERE patient_id IN (SELECT id FROM derick_to_delete);
DELETE FROM patient WHERE id IN (SELECT id FROM derick_to_delete);
COMMIT;

SELECT id, hospital_no, concat_ws(' ', given_name, middle_name, family_name) AS full_name
FROM patient
ORDER BY created_at;
