-- Permission model per docs/spec/01-architecture.md §6.2 and root CLAUDE.md
-- "Authorisation" invariants. Code checks permissions, never role names —
-- roles exist only as named, seeded bundles of permissions.

CREATE TABLE permission (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        TEXT NOT NULL UNIQUE CHECK (code = upper(code)),
    description TEXT NOT NULL,
    -- Drives the ADMIN guard below. Admin has no default clinical data
    -- access — break-glass only (root CLAUDE.md "Authorisation").
    is_clinical BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code       TEXT NOT NULL UNIQUE CHECK (code = upper(code)),
    name       TEXT NOT NULL,
    is_system  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role_permission (
    role_id       UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Enforced at the database, not the admin UI: granting ADMIN a clinical
-- permission must fail outright, on the seed data and on every insert after
-- it, forever — not just at migration time.
CREATE FUNCTION fn_guard_admin_no_clinical_permission() RETURNS TRIGGER AS $$
DECLARE
    v_role_code TEXT;
    v_is_clinical BOOLEAN;
BEGIN
    SELECT code INTO v_role_code FROM role WHERE id = NEW.role_id;
    SELECT is_clinical INTO v_is_clinical FROM permission WHERE id = NEW.permission_id;

    IF v_role_code = 'ADMIN' AND v_is_clinical THEN
        RAISE EXCEPTION
            'ADMIN cannot hold clinical permission (role_id=%, permission_id=%). Admin access to clinical data is break-glass only.',
            NEW.role_id, NEW.permission_id
            USING ERRCODE = '23514'; -- check_violation
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_guard_admin_no_clinical_permission
    BEFORE INSERT OR UPDATE ON role_permission
    FOR EACH ROW
    EXECUTE FUNCTION fn_guard_admin_no_clinical_permission();

CREATE TABLE role_assignment (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES app_user(id),
    role_id      UUID NOT NULL REFERENCES role(id),
    scope_type   TEXT NOT NULL
                     CHECK (scope_type IN ('GLOBAL', 'FACILITY', 'DEPARTMENT', 'WARD', 'CLINIC', 'STORE')),
    -- Polymorphic on purpose: scope_id points at facility/department/ward/
    -- clinic/store depending on scope_type, so it cannot carry a single FK.
    -- The application layer is the arbiter of which table it resolves
    -- against; AuthorizationServiceTest proves the walk-up does this by id,
    -- never by name, which is what stops a ward-scoped user at one facility
    -- from resolving into a same-named ward at another.
    scope_id     UUID,
    valid_from   TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to     TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    granted_by   UUID REFERENCES app_user(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((scope_type = 'GLOBAL') = (scope_id IS NULL)),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE INDEX idx_role_assignment_user ON role_assignment(user_id);
CREATE INDEX idx_role_assignment_scope ON role_assignment(scope_type, scope_id);

-- Requests to elevate a user's access. Second-person constraint: the reviewer
-- can never be the requester (root CLAUDE.md "Authorisation").
CREATE TABLE elevation_request (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requested_by  UUID NOT NULL REFERENCES app_user(id),
    role_id       UUID REFERENCES role(id),
    permission_id UUID REFERENCES permission(id),
    scope_type    TEXT CHECK (scope_type IN ('GLOBAL', 'FACILITY', 'DEPARTMENT', 'WARD', 'CLINIC', 'STORE')),
    scope_id      UUID,
    justification TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by   UUID REFERENCES app_user(id),
    reviewed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (reviewed_by IS DISTINCT FROM requested_by),
    CHECK ((role_id IS NOT NULL) OR (permission_id IS NOT NULL))
);

CREATE INDEX idx_elevation_request_requester ON elevation_request(requested_by);
CREATE INDEX idx_elevation_request_pending ON elevation_request(created_at)
    WHERE status = 'PENDING';

-- Seed the ten named role bundles from §6.2. Fine-grained permission codes
-- are added module-by-module as each module ships — seeding the full ~150
-- now would mean guessing at codes the per-role specs (docs/spec/05..18)
-- haven't been implemented against yet. This phase seeds only what Phase 0
-- itself needs: enough permissions to prove the scope/expiry/relationship
-- and ADMIN-guard mechanics, on both sides of the clinical line.
INSERT INTO role (code, name) VALUES
    ('ADMIN', 'Administrator'),
    ('RECEPTIONIST', 'Receptionist'),
    ('TRIAGE_NURSE', 'Triage Nurse'),
    ('DOCTOR', 'Doctor'),
    ('SPECIALIST_DOCTOR', 'Specialist Doctor'),
    ('LAB_TECHNICIAN', 'Lab Technician'),
    ('PHARMACIST', 'Pharmacist'),
    ('ACCOUNTANT', 'Accountant'),
    ('WARD_NURSE', 'Ward Nurse'),
    ('NURSE', 'Nurse');

INSERT INTO permission (code, description, is_clinical) VALUES
    ('USER_MANAGE', 'Create, update, and deactivate user accounts', FALSE),
    ('ROLE_ASSIGN', 'Grant and revoke scoped role assignments', FALSE),
    ('AUDIT_LOG_READ', 'Read the audit log', FALSE),
    ('FACILITY_CONFIG', 'Manage facility, department, and ward records', FALSE),
    ('PATIENT_REGISTER', 'Register and edit patient demographics', FALSE),
    ('ENCOUNTER_OPEN', 'Open and close encounters', FALSE),
    ('CLINICAL_NOTE_READ', 'Read clinical notes', TRUE),
    ('CLINICAL_NOTE_WRITE', 'Author and amend clinical notes', TRUE),
    ('MEDICATION_ADMINISTER', 'Chart medication administration', TRUE);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'ADMIN' AND p.code IN ('USER_MANAGE', 'ROLE_ASSIGN', 'AUDIT_LOG_READ', 'FACILITY_CONFIG');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'RECEPTIONIST' AND p.code IN ('PATIENT_REGISTER', 'ENCOUNTER_OPEN');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code = 'TRIAGE_NURSE' AND p.code IN ('CLINICAL_NOTE_READ');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code IN ('DOCTOR', 'SPECIALIST_DOCTOR') AND p.code IN ('CLINICAL_NOTE_READ', 'CLINICAL_NOTE_WRITE');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.code IN ('WARD_NURSE', 'NURSE') AND p.code IN ('MEDICATION_ADMINISTER');
