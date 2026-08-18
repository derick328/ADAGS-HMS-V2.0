-- Phase 5, pharmacy build brief item 1: item catalogue and inventory
-- foundation. docs/spec/12-pharmacist.md §7.2/§9.1/§12,
-- 14-controlled-medicines-tanzania.md §2.
--
-- Stock is an append-only movement ledger (root CLAUDE.md "Data") — neither
-- stock_batch nor stock_balance carries a quantity a caller can overwrite.
-- stock_movement is the only place a quantity is ever written; stock_balance
-- is derived and materialised from it inside the same transaction as each
-- movement (same discipline as invoice.outstanding_total_minor in
-- V17__payment_and_cashier_shift.sql).
--
-- item, supplier carry no facility_id — definitions, like patient or
-- icd10_code (root CLAUDE.md "Facility"). store does, since a store is a
-- physical location.
--
-- No CHECK enum beyond the eight named regulatory_schedule values here —
-- the CONTROLLED write-path block (catalogue activation, restock,
-- dispensing) is repository/domain-layer behaviour, build brief item 2,
-- not this migration.

CREATE TABLE supplier (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        TEXT NOT NULL UNIQUE,
    name        TEXT NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE store (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         TEXT NOT NULL,
    type         TEXT NOT NULL,
    facility_id  UUID NOT NULL REFERENCES facility(id),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 12-pharmacist.md §10.1: regulatory_schedule NOT NULL DEFAULT
-- 'UNCLASSIFIED' — the legal default per 14-*.md §5 (regulation 6(1)). D9's
-- confirmed exception is that UNCLASSIFIED is nonetheless dispensable,
-- unlike CONTROLLED — see RegulatorySchedule.isDispensable().
CREATE TABLE item (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                 TEXT NOT NULL UNIQUE,
    name                 TEXT NOT NULL,
    classification       TEXT NOT NULL,
    form                 TEXT NOT NULL,
    strength             TEXT,
    unit                 TEXT NOT NULL,
    regulatory_schedule  TEXT NOT NULL DEFAULT 'UNCLASSIFIED'
        CHECK (regulatory_schedule IN (
            'CONTROLLED', 'PRESCRIPTION_ONLY', 'PHARMACY_ONLY_BTC', 'PHARMACY_ONLY_OTC',
            'GENERAL_SALES', 'ADDO_PRESCRIPTION', 'RADIOPHARMACEUTICAL', 'UNCLASSIFIED')),
    reorder_level        INTEGER NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    active               BOOLEAN NOT NULL DEFAULT FALSE,
    created_by           UUID NOT NULL REFERENCES app_user(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 12-pharmacist.md §7.2: batch_number, expiry_date, unit_cost, supplier and
-- delivery_note_ref are all mandatory on restock (D3, D6) — "the minimum
-- control on stock creation... without it a pharmacist can add 10,000 units
-- of anything with no paper trail." unit_cost lives here, never on item —
-- the same drug is bought at different prices on different deliveries.
CREATE TABLE stock_batch (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id                   UUID NOT NULL REFERENCES item(id),
    store_id                  UUID NOT NULL REFERENCES store(id),
    batch_number              TEXT NOT NULL,
    expiry_date               DATE NOT NULL,
    unit_cost_amount_minor    BIGINT NOT NULL CHECK (unit_cost_amount_minor >= 0),
    unit_cost_currency_code   TEXT NOT NULL DEFAULT 'TZS',
    supplier_id               UUID NOT NULL REFERENCES supplier(id),
    delivery_note_ref         TEXT NOT NULL,
    status                    TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'QUARANTINED', 'EXPIRED', 'WRITTEN_OFF')),
    received_by               UUID NOT NULL REFERENCES app_user(id),
    received_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    notes                     TEXT,
    -- §7.2: "Expiry date... Must be future-dated" at the moment of restock.
    CHECK (expiry_date > received_at::date)
);

CREATE INDEX idx_stock_batch_item ON stock_batch (item_id);
-- §5.3/§7.3: FEFO selection and the 90-day expiry watchlist both scan
-- active batches ordered by expiry — this index serves both.
CREATE INDEX idx_stock_batch_item_expiry ON stock_batch (item_id, expiry_date) WHERE status = 'ACTIVE';

-- Append-only. The repository layer offers insert() and nothing else — no
-- UPDATE or DELETE path is ever exposed (root CLAUDE.md "Data": "Stock is
-- an append-only movement ledger. Never a mutable quantity column.").
CREATE TABLE stock_movement (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id        UUID NOT NULL REFERENCES item(id),
    batch_id       UUID NOT NULL REFERENCES stock_batch(id),
    store_id       UUID NOT NULL REFERENCES store(id),
    qty_delta      INTEGER NOT NULL CHECK (qty_delta != 0),
    movement_type  TEXT NOT NULL CHECK (movement_type IN
                       ('RECEIPT', 'DISPENSE', 'TRANSFER_OUT', 'TRANSFER_IN',
                        'ADJUSTMENT', 'WASTAGE', 'WRITE_OFF', 'RETURN')),
    reference_type TEXT,
    reference_id   UUID,
    actor_id       UUID NOT NULL REFERENCES app_user(id),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_movement_batch ON stock_movement (batch_id);

-- Materialised, derived from stock_movement — root CLAUDE.md "Concurrency":
-- CHECK (quantity >= 0) is the real guard against two concurrent dispenses
-- oversubscribing the same batch; a read-then-write check in application
-- code loses that race. Every write to this table happens inside the same
-- transaction as the stock_movement row that justifies it.
CREATE TABLE stock_balance (
    item_id   UUID NOT NULL REFERENCES item(id),
    batch_id  UUID NOT NULL REFERENCES stock_batch(id),
    store_id  UUID NOT NULL REFERENCES store(id),
    quantity  INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    PRIMARY KEY (item_id, batch_id, store_id)
);
