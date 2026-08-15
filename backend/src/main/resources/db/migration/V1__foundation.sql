-- Phase 0 foundation: facility hierarchy (for scope walk-up), users, the
-- transactional outbox, and the append-only audit log.
-- Permissions, roles, and role assignments are V2 — this file only carries
-- what they depend on (app_user) and what scope resolution walks up through.

CREATE TABLE facility (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        TEXT NOT NULL UNIQUE,
    name        TEXT NOT NULL,
    timezone    TEXT NOT NULL DEFAULT 'Africa/Dar_es_Salaam',
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE department (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id  UUID NOT NULL REFERENCES facility(id),
    code         TEXT NOT NULL,
    name         TEXT NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (facility_id, code)
);

CREATE TABLE ward (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id      UUID NOT NULL REFERENCES department(id),
    code               TEXT NOT NULL,
    name               TEXT NOT NULL,
    gender_restriction TEXT,
    bed_count          INTEGER NOT NULL DEFAULT 0,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (department_id, code)
);

CREATE INDEX idx_department_facility ON department(facility_id);
CREATE INDEX idx_ward_department ON ward(department_id);

CREATE TABLE app_user (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username              TEXT NOT NULL UNIQUE,
    email                 TEXT UNIQUE,
    phone                 TEXT,
    password_hash         TEXT NOT NULL,
    status                TEXT NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    must_change_password  BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at         TIMESTAMPTZ,
    failed_attempts       INTEGER NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Nullable: the V3 bootstrap admin is created by nothing but the install
    -- secret. Every user after that is created by an admin.
    created_by            UUID REFERENCES app_user(id)
);

-- Domain events, delivered via the transactional outbox so a crash between
-- commit and delivery cannot lose one (see docs/spec/01-architecture.md §3).
CREATE TABLE outbox_event (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type    TEXT NOT NULL,
    payload       JSONB NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at  TIMESTAMPTZ
);

CREATE INDEX idx_outbox_event_unprocessed ON outbox_event(occurred_at)
    WHERE processed_at IS NULL;

-- Append-only, partitioned by month, and captures reads as well as writes for
-- patient data ("who looked at this patient's record" per §5.13). Break-glass
-- rows always carry a justification.
CREATE TABLE audit_log (
    id               UUID NOT NULL DEFAULT gen_random_uuid(),
    actor_user_id    UUID REFERENCES app_user(id),
    action           TEXT NOT NULL,
    resource_type    TEXT NOT NULL,
    resource_id      UUID,
    patient_id       UUID,
    is_break_glass   BOOLEAN NOT NULL DEFAULT FALSE,
    justification    TEXT,
    ip               INET,
    user_agent       TEXT,
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, occurred_at),
    CHECK (NOT is_break_glass OR justification IS NOT NULL)
) PARTITION BY RANGE (occurred_at);

-- Static partitions for the first three months. Pre-creating the next
-- partition on a schedule is an operational job for a later phase — this is
-- the safety net so an unpartitioned write never fails outright.
CREATE TABLE audit_log_2026_08 PARTITION OF audit_log
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE audit_log_2026_09 PARTITION OF audit_log
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE audit_log_2026_10 PARTITION OF audit_log
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE audit_log_default PARTITION OF audit_log DEFAULT;

CREATE INDEX idx_audit_log_actor ON audit_log(actor_user_id, occurred_at);
CREATE INDEX idx_audit_log_patient ON audit_log(patient_id, occurred_at)
    WHERE patient_id IS NOT NULL;
CREATE INDEX idx_audit_log_break_glass ON audit_log(occurred_at)
    WHERE is_break_glass;
