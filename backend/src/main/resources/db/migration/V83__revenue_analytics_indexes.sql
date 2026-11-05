-- Index-only migration for the admin revenue analytics reads (18-admin.md).
-- No table, column, constraint or row is touched: every statement here is a
-- CREATE INDEX IF NOT EXISTS, so this is reversible by dropping the indexes
-- and changes no accounting semantics.
--
-- Each one backs a date-ranged aggregate that previously had no usable
-- access path:
--
--   payment(received_at)            every "collected in period" aggregate
--                                   filters p.received_at::date BETWEEN.
--                                   Existing indexes cover patient and
--                                   cashier_shift only.
--   invoice_line(created_at)        every "billed in period" aggregate.
--                                   Existing index covers invoice_id only.
--   invoice(facility_id, state)     the facility filter on every query here,
--                                   plus the state predicate that excludes
--                                   DRAFT/VOID from billed and selects the
--                                   receivable buckets. Existing index
--                                   covers encounter_id only.
--   invoice(facility_id, created_at) the settled/issued invoice counts.
--
-- A btree on a timestamptz column does not serve a ::date cast directly,
-- so these are range-scan helpers for the BETWEEN bounds rather than exact
-- expression matches — which is what the planner needs here, since the
-- casts are monotonic in the underlying column.
CREATE INDEX IF NOT EXISTS idx_payment_received_at ON payment (received_at);
CREATE INDEX IF NOT EXISTS idx_invoice_line_created_at ON invoice_line (created_at);
CREATE INDEX IF NOT EXISTS idx_invoice_facility_state ON invoice (facility_id, state);
CREATE INDEX IF NOT EXISTS idx_invoice_facility_created_at ON invoice (facility_id, created_at);
