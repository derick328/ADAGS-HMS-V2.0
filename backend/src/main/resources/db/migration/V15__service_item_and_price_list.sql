-- Phase 3, billing gate, item 1: ServiceItem, PriceList, PriceListEntry.
-- docs/spec/02-billing-architecture.md §4.2/§11, 05a-receptionist-decisions.md
-- D5, root CLAUDE.md "Billing".
--
-- payer_category is deliberately its own vocabulary, not
-- shared.kernel.PayerType (CASH/INSURANCE) — that enum is the coarse
-- encounter-level split already load-bearing everywhere (Encounter,
-- triage); this is a finer pricing dimension. NHIF gets its own value
-- because it's named explicitly and is the dominant scheme here;
-- PRIVATE_SCHEME buckets everything else until the insurance module
-- (SchemeCoverageRule, a later phase) can price per named scheme.
--
-- Neither table carries facility_id: root CLAUDE.md "Facility" — these are
-- definitions ("what a consultation costs"), not occurrences at a place,
-- same reasoning as icd10_code carrying no facility_id.

CREATE TABLE service_item (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        TEXT NOT NULL UNIQUE,
    name        TEXT NOT NULL,
    -- LAB/PROCEDURE/PHARMACY have no caller yet (orders/laboratory/
    -- pharmacy modules don't exist) — modelled now so this column never
    -- needs a later rename, same reasoning as VitalSignsContext's
    -- CONSULTATION value. (PHARMACY added in V25 for prescription
    -- charges — pharmacy Item.code is used directly as a service_item
    -- code, so a matching row here plus a PriceListEntry is what
    -- postServiceCharge resolves at prescribing time.)
    category    TEXT NOT NULL CHECK (category IN ('CONSULTATION', 'LAB', 'PROCEDURE', 'PHARMACY')),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One list per payer category — modelled for all five now even though
-- only CASH has entries seeded below; NHIF/PRIVATE_SCHEME/CORPORATE/STAFF
-- exist as configuration targets an admin fills in later, not as guessed
-- numbers (root CLAUDE.md "Billing": "A missing price... never defaults to
-- zero" — there must be a row to attach a real price to once one exists).
CREATE TABLE price_list (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payer_category TEXT NOT NULL UNIQUE
                       CHECK (payer_category IN ('CASH', 'NHIF', 'PRIVATE_SCHEME', 'CORPORATE', 'STAFF')),
    name           TEXT NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- §4.2: "Prices are never in code... versioned... A change closes the
-- current entry and opens a new one, so a March invoice always resolves
-- March's price." valid_from/valid_to model that at the ENTRY level (not
-- the whole list) so re-pricing one item doesn't touch every other row.
-- The "always resolves March's price" guarantee itself comes from
-- InvoiceLine snapshotting the resolved amount at creation time (item 2)
-- — the same division of responsibility as Diagnosis.icd10_title_snapshot,
-- not from this table supporting historical-date queries.
CREATE TABLE price_list_entry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    price_list_id   UUID NOT NULL REFERENCES price_list(id),
    service_item_id UUID NOT NULL REFERENCES service_item(id),
    amount_minor    BIGINT NOT NULL CHECK (amount_minor >= 0),
    currency_code   TEXT NOT NULL DEFAULT 'TZS',
    valid_from      DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to        DATE,
    created_by      UUID NOT NULL REFERENCES app_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- >= not >: a same-day correction (opened this morning, closed this
    -- afternoon once someone spots a typo) is a legitimate real case, not
    -- a data error to reject.
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

-- One current price per (list, item) — the same current-row pattern as
-- vital_reference_range and protocol_setting.
CREATE UNIQUE INDEX idx_price_list_entry_current ON price_list_entry (price_list_id, service_item_id)
    WHERE valid_to IS NULL;

CREATE INDEX idx_price_list_entry_item ON price_list_entry (service_item_id);

-- Seed data -----------------------------------------------------------------

INSERT INTO price_list (payer_category, name)
VALUES ('CASH', 'Cash'),
       ('NHIF', 'NHIF'),
       ('PRIVATE_SCHEME', 'Private Insurance Schemes'),
       ('CORPORATE', 'Corporate Accounts'),
       ('STAFF', 'Staff');

INSERT INTO service_item (code, name, category)
VALUES ('CONSULT_GENERAL', 'General consultation', 'CONSULTATION'),
       ('CONSULT_SPECIALIST', 'Specialist consultation', 'CONSULTATION');

-- 05a-receptionist-decisions.md D5: "Cash prices: general doctor 10,000/=,
-- specialist 40,000/=." Only CASH is seeded — no numbers exist for the
-- other four categories, and a guessed price is worse than a missing one
-- that blocks with an explicit error.
INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, created_by)
SELECT pl.id, si.id, v.amount_minor, (SELECT id FROM app_user ORDER BY created_at ASC LIMIT 1)
FROM price_list pl
JOIN (VALUES ('CONSULT_GENERAL', 1000000::bigint), ('CONSULT_SPECIALIST', 4000000::bigint))
     AS v(code, amount_minor) ON TRUE
JOIN service_item si ON si.code = v.code
WHERE pl.payer_category = 'CASH';
