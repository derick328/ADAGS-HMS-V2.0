-- Local test-data reset. Run only after taking a pg_dump backup.
-- Keeps the latest Derick Gabriel Mhidze patient profile and registration details.
-- TRUNCATE CASCADE permanently removes all patient-linked visits, invoices,
-- payments, and clinical records, including the kept patient's test visit.
\set ON_ERROR_STOP on
BEGIN;

LOCK TABLE patient IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF (SELECT count(*) FROM patient) <> 8 THEN
        RAISE EXCEPTION 'Expected exactly 8 patients; database changed. No data was removed.';
    END IF;
    IF (SELECT count(*) FROM patient
        WHERE id = 'c3babc44-f0bd-4561-b707-f46b75ff33b7'
          AND hospital_no = 'MAIN-2026-000011'
          AND lower(regexp_replace(
                btrim(concat_ws(' ', given_name, middle_name, family_name)),
                '[[:space:]]+', ' ', 'g')) = 'derick gabriel mhidze'
          AND merged_into_id IS NULL) <> 1 THEN
        RAISE EXCEPTION 'The Derick record to keep does not match. No data was removed.';
    END IF;
END $$;

CREATE TEMP TABLE keep_patient ON COMMIT DROP AS
    SELECT * FROM patient WHERE id = 'c3babc44-f0bd-4561-b707-f46b75ff33b7';
CREATE TEMP TABLE keep_identifiers ON COMMIT DROP AS
    SELECT * FROM patient_identifier WHERE patient_id = 'c3babc44-f0bd-4561-b707-f46b75ff33b7';
CREATE TEMP TABLE keep_next_of_kin ON COMMIT DROP AS
    SELECT * FROM next_of_kin WHERE patient_id = 'c3babc44-f0bd-4561-b707-f46b75ff33b7';
CREATE TEMP TABLE keep_addresses ON COMMIT DROP AS
    SELECT * FROM patient_address WHERE patient_id = 'c3babc44-f0bd-4561-b707-f46b75ff33b7';
CREATE TEMP TABLE keep_policies ON COMMIT DROP AS
    SELECT * FROM insurance_policy WHERE patient_id = 'c3babc44-f0bd-4561-b707-f46b75ff33b7';

TRUNCATE TABLE patient CASCADE;

INSERT INTO patient SELECT * FROM keep_patient;
INSERT INTO patient_identifier SELECT * FROM keep_identifiers;
INSERT INTO next_of_kin SELECT * FROM keep_next_of_kin;
INSERT INTO patient_address SELECT * FROM keep_addresses;
INSERT INTO insurance_policy SELECT * FROM keep_policies;

DO $$
BEGIN
    IF (SELECT count(*) FROM patient) <> 1
       OR NOT EXISTS (SELECT 1 FROM patient
                      WHERE id = 'c3babc44-f0bd-4561-b707-f46b75ff33b7') THEN
        RAISE EXCEPTION 'Patient verification failed; rolling back the reset.';
    END IF;
END $$;

COMMIT;

SELECT id, hospital_no, concat_ws(' ', given_name, middle_name, family_name) AS full_name
FROM patient;
