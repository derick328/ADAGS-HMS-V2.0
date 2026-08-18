-- Phase 3 follow-up: replace BillingFacadeImpl's hardcoded
-- DOCTOR/SPECIALIST_DOCTOR role-code branching with a data-driven mapping.
--
-- Root CLAUDE.md "Authorisation" says check permissions, never role names —
-- but two BILL_AS_* permission codes would leave a doctor holding both with
-- no deterministic price, so the fix is not a permission rename. Pricing
-- keyed by a provider's role is a catalogue lookup, not an authorization
-- decision (see the comment BillingFacadeImpl already carried). The actual
-- problem was that the mapping lived in Java constants instead of a table:
-- a hospital adding a registrar rate had to redeploy. This is the same
-- current-row pattern as price_list_entry and vital_reference_range.
--
-- priority resolves the case where a provider holds multiple mapped roles
-- (e.g. both DOCTOR and SPECIALIST_DOCTOR) — the highest-priority current
-- row among the roles actually held wins, so "which price applies" is a
-- data-ordered lookup, never a judgement call on which role is "the
-- acceptable kind."
CREATE TABLE provider_billing_category (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_code         TEXT NOT NULL,
    service_item_code TEXT NOT NULL REFERENCES service_item(code),
    priority          INT NOT NULL,
    effective_from    DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to      DATE,
    created_by        UUID NOT NULL REFERENCES app_user(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- >= not >: a same-day correction is legitimate, not a data error to
    -- reject (V15__service_item_and_price_list.sql, V9__vital_reference_range.sql).
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- One current mapping per role code — never edited in place.
CREATE UNIQUE INDEX idx_provider_billing_category_current ON provider_billing_category (role_code)
    WHERE effective_to IS NULL;

-- The mapping BillingFacadeImpl previously hardcoded. SPECIALIST_DOCTOR
-- outranks DOCTOR so a provider holding both still resolves deterministically.
INSERT INTO provider_billing_category (role_code, service_item_code, priority, created_by)
SELECT 'SPECIALIST_DOCTOR', 'CONSULT_SPECIALIST', 100, id FROM app_user ORDER BY created_at ASC LIMIT 1;

INSERT INTO provider_billing_category (role_code, service_item_code, priority, created_by)
SELECT 'DOCTOR', 'CONSULT_GENERAL', 10, id FROM app_user ORDER BY created_at ASC LIMIT 1;
