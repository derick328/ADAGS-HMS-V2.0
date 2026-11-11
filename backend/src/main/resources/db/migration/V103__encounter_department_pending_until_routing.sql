-- Walk-in encounters enter the facility triage queue before a clinical
-- department is known. Booked clinic encounters may retain their department.
ALTER TABLE encounter ALTER COLUMN department_id DROP NOT NULL;
