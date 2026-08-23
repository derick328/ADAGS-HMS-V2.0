-- 18-admin.md §11: "Admin cannot silently elevate themselves. Granting
-- ADMIN, or granting any permission to one's own account, requires a second
-- admin and alerts regardless." The full workflow (second-admin approval via
-- elevation_request, CLINICAL_LEAD alerting) is not built yet — this is the
-- hard floor under it: nobody, admin or otherwise, can grant themselves a
-- role assignment at all. Same second-person-constraint discipline as
-- CHECK (created_by != approved_by) on expenses (root CLAUDE.md
-- "Authorisation"). granted_by is nullable (the V3 bootstrap admin's own
-- assignment has none), so IS DISTINCT FROM — not != — is required: a NULL
-- granted_by must not vacuously satisfy the check.
ALTER TABLE role_assignment
    ADD CONSTRAINT role_assignment_no_self_grant
        CHECK (granted_by IS DISTINCT FROM user_id);
