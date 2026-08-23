-- No department has ever existed in this schema — department table has
-- zero rows on every install. encounter.department_id is NOT NULL, so
-- nothing anywhere in the system (triage, the doctor queue, billing gates
-- keyed to a department) can open an encounter at all without one. Same
-- reasoning and same pattern as V4's bootstrap facility row: department
-- onboarding is an admin workflow that doesn't exist yet (18-admin.md §12),
-- but the system needs a real one to function starting now.
INSERT INTO department (facility_id, code, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'GENERAL', 'General')
ON CONFLICT (facility_id, code) DO NOTHING;
