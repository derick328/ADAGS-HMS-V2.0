-- Three Phase 0 schema fixes found during review of V1/V2:
--
--   1. audit_log.facility_id — root CLAUDE.md's "Facility" convention says
--      everything that happened at a place carries facility_id. AUDIT_LOG_READ
--      (V2) can be granted at FACILITY scope, but audit_log had no column to
--      enforce that scope against.
--   2. elevation_request was missing the GLOBAL/scope_id pairing check that
--      role_assignment already has, despite carrying the same two columns.
--   3. audit_log partition maintenance — V1 only pre-created three static
--      monthly partitions (Aug-Oct 2026) plus a DEFAULT catch-all. This adds
--      the idempotent routine that AuditLogPartitionMaintenanceJob calls on a
--      schedule to keep twelve months of real partitions ahead of the DEFAULT
--      one, which should never actually receive a row in practice.

-- (1) audit_log.facility_id ------------------------------------------------

-- Bootstrap facility. docs/spec/01-architecture.md §1 assumes a single
-- hospital for v1 — this is that hospital, not a placeholder tenant. Facility
-- onboarding as an ADMIN workflow is a later phase, but audit_log rows need a
-- real, non-null facility_id starting now. Seeded with a fixed id (rather
-- than gen_random_uuid()) so the DEFAULT below can name it directly — the
-- same "one well-known bootstrap row" pattern as V3__BootstrapAdmin.
INSERT INTO facility (id, code, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'MAIN', 'Main facility')
ON CONFLICT (code) DO NOTHING;

-- NOT NULL, not nullable: every audit event happens somewhere. The original
-- instruction for this column was `BIGINT ... DEFAULT 1`; adapted to this
-- schema's UUID primary keys, the bootstrap facility above is the "1". This
-- default exists to satisfy the NOT NULL constraint for the write paths that
-- don't yet resolve a real per-request facility (see JdbcAuditRecorder) — it
-- is a Phase 0 stopgap, not a design decision to keep long-term.
ALTER TABLE audit_log
    ADD COLUMN facility_id UUID NOT NULL
        REFERENCES facility(id)
        DEFAULT '00000000-0000-0000-0000-000000000001';

CREATE INDEX idx_audit_log_facility ON audit_log (facility_id, occurred_at DESC);

-- (2) elevation_request scope guard ----------------------------------------

ALTER TABLE elevation_request
    ADD CONSTRAINT elevation_request_global_scope_check
        CHECK ((scope_type = 'GLOBAL') = (scope_id IS NULL));

-- (3) audit_log partition maintenance ---------------------------------------

-- Idempotent: safe to call for a month whose partition already exists (the
-- three V1 static partitions included) and safe to call more than once for
-- the same month.
CREATE FUNCTION fn_ensure_audit_log_partition(p_month DATE) RETURNS VOID AS $$
DECLARE
    v_partition_name TEXT := 'audit_log_' || to_char(p_month, 'YYYY_MM');
    v_start DATE := date_trunc('month', p_month);
    v_end DATE := v_start + INTERVAL '1 month';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = v_partition_name) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF audit_log FOR VALUES FROM (%L) TO (%L)',
            v_partition_name, v_start, v_end
        );
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Bootstrap the next twelve months immediately, so a fresh install is covered
-- from day one rather than waiting for AuditLogPartitionMaintenanceJob's first
-- scheduled run.
DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 0..11 LOOP
        PERFORM fn_ensure_audit_log_partition(
            (date_trunc('month', now()) + (i || ' months')::INTERVAL)::DATE
        );
    END LOOP;
END;
$$;
