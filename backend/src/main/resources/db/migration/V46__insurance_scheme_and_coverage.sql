-- Phase 7, item 3 (part 1): insurance schema and coverage-rule
-- resolution. 02-billing-architecture.md §6. Schema only — no line split,
-- no gate clause (parts 2 and 3).

-- V6's insurance_scheme was "a minimal stub — just enough for
-- InsurancePolicy to reference a managed list instead of free text."
-- Becomes real here.
ALTER TABLE insurance_scheme
    ADD COLUMN claim_submission_method TEXT,
    ADD COLUMN contact                 TEXT,
    ADD COLUMN payment_terms_days      INT CHECK (payment_terms_days IS NULL OR payment_terms_days > 0);

-- §6.2: "Rules are versioned — a claim must be assessable against the
-- rule that applied on the service date." Same effective_from/effective_to
-- shape as price_list_entry and provider_billing_category, but unlike
-- those two — which only ever resolve "current" (see V15's comment: the
-- historical guarantee there comes from InvoiceLine snapshotting the
-- resolved amount, not from date-ranged queries) — coverage-rule
-- resolution genuinely needs an as-of-date lookup: a claim assembled in
-- November must resolve against whatever rule applied when the service
-- was actually rendered in September, which is why resolution takes a
-- serviceDate parameter rather than only ever reading the current row.
--
-- service_category and service_item_id are both nullable, but exactly one
-- must be set — a category rule (the general case for a service line) and
-- an item-specific rule (a named override) are two different kinds of row,
-- not a row that means both at once. "A service-item-specific rule wins
-- over a category rule for the same date" is a resolution-time precedence,
-- not something either row encodes itself.
CREATE TABLE scheme_coverage_rule (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scheme_id         UUID NOT NULL REFERENCES insurance_scheme(id),
    -- Deliberately the insurance module's own vocabulary, not a reference
    -- to billing.domain.ServiceItemCategory — root CLAUDE.md "Modules":
    -- no cross-module entity imports. Kept in sync by hand with
    -- service_item's own CHECK (V15/V32) since both describe the same
    -- real-world categories.
    service_category  TEXT CHECK (service_category IN ('CONSULTATION', 'LAB', 'PROCEDURE', 'PHARMACY', 'WARD')),
    service_item_id   UUID REFERENCES service_item(id),
    coverage_type     TEXT NOT NULL CHECK (coverage_type IN ('FULL', 'PERCENTAGE', 'FIXED_AMOUNT', 'EXCLUDED')),
    -- NUMERIC, never FLOAT/DOUBLE (root CLAUDE.md "Money": never floating
    -- point) — exact decimal even though this isn't a money column
    -- itself. Meaning depends on coverage_type: 0-100 for PERCENTAGE,
    -- minor units for FIXED_AMOUNT; irrelevant (NULL) for FULL/EXCLUDED.
    coverage_value    NUMERIC(14, 4),
    requires_preauth  BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from    DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to      DATE,
    created_by        UUID NOT NULL REFERENCES app_user(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((service_category IS NOT NULL) != (service_item_id IS NOT NULL)),
    -- >= not >: a same-day correction is legitimate, not a data error to
    -- reject (V15, V19).
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CHECK (coverage_type NOT IN ('PERCENTAGE', 'FIXED_AMOUNT') OR coverage_value IS NOT NULL),
    CHECK (coverage_type != 'PERCENTAGE' OR (coverage_value >= 0 AND coverage_value <= 100))
);

-- One current (effective_to IS NULL) row per scope — item-specific and
-- category rules are separate scopes, so each gets its own partial index.
CREATE UNIQUE INDEX idx_scheme_coverage_rule_current_item
    ON scheme_coverage_rule (scheme_id, service_item_id)
    WHERE effective_to IS NULL AND service_item_id IS NOT NULL;
CREATE UNIQUE INDEX idx_scheme_coverage_rule_current_category
    ON scheme_coverage_rule (scheme_id, service_category)
    WHERE effective_to IS NULL AND service_category IS NOT NULL;
CREATE INDEX idx_scheme_coverage_rule_scheme ON scheme_coverage_rule (scheme_id);

-- ⚠ Seed no coverage rules. NHIF's actual terms come from the scheme, and
-- a guessed percentage misprices every insured patient (same reasoning as
-- price_list_entry seeding only CASH).

-- claim_no follows invoice_number's two-step pattern exactly (V16): NULL
-- at DRAFT, allocated from the gapless sequence only once a claim is
-- actually submitted — not built in this part, but the schema shouldn't
-- need a later migration to express it correctly.
CREATE TABLE insurance_claim (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id       UUID NOT NULL REFERENCES invoice(id),
    scheme_id        UUID NOT NULL REFERENCES insurance_scheme(id),
    claim_no         TEXT UNIQUE,
    state            TEXT NOT NULL DEFAULT 'DRAFT'
                         CHECK (state IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'SETTLED')),
    submitted_at     TIMESTAMPTZ,
    approved_minor   BIGINT CHECK (approved_minor IS NULL OR approved_minor >= 0),
    rejected_reason  TEXT,
    -- No FK: claim batching (grouping claims for one submission run to
    -- the scheme) is a later mechanism, not built in this part.
    batch_id         UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (state = 'DRAFT' OR claim_no IS NOT NULL),
    CHECK (state != 'REJECTED' OR rejected_reason IS NOT NULL)
);

CREATE INDEX idx_insurance_claim_invoice ON insurance_claim (invoice_id);
CREATE INDEX idx_insurance_claim_scheme ON insurance_claim (scheme_id);
