-- Phase 2, clinical core, item 3: PaymentBypass (billing module scaffold).
-- 02-billing-architecture.md §5, root CLAUDE.md "Billing": "An active
-- PaymentBypass satisfies the gate. IMMEDIATE triage priority creates one
-- automatically." The rest of the billing module (PriceList, PriceListEntry,
-- Invoice, the gate function itself) does not exist yet — this table is the
-- one piece of it item 3 needs to be real today, because bypass creation
-- happens unconditionally on an IMMEDIATE submission, independent of
-- whether pricing exists yet. BillingFacade.postConsultationCharge is the
-- seam that still fails until the rest of the module is built — see
-- BillingModuleNotImplementedException.

CREATE TABLE payment_bypass (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id  UUID NOT NULL REFERENCES encounter(id),
    -- 02-billing-architecture.md §5.2: URGENT/below require clinician
    -- authorisation (a different source value) — not built yet, only the
    -- triage nurse's automatic IMMEDIATE grant exists today.
    source        TEXT NOT NULL CHECK (source IN ('TRIAGE_IMMEDIATE')),
    -- 06-triage.md §4.1: the nurse's reason selection (or its free text for
    -- OTHER) — the justification lands in the record with the grant.
    reason        TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    granted_by    UUID NOT NULL REFERENCES app_user(id),
    granted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- §5.3: "The attending doctor revokes once the patient is stabilised."
    -- No caller exists yet (no doctor module) — modelled now so the status
    -- transition never needs a later column addition.
    revoked_by    UUID REFERENCES app_user(id),
    revoked_at    TIMESTAMPTZ,
    CHECK ((status = 'ACTIVE' AND revoked_by IS NULL AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_by IS NOT NULL AND revoked_at IS NOT NULL))
);

-- Serves both "history for this encounter" and "is there an active bypass
-- right now" (02-billing-architecture.md §3: "active_payment_bypass_exists
-- (encounter)", the gate-function clause a future billing module calls).
CREATE INDEX idx_payment_bypass_encounter ON payment_bypass (encounter_id, status);
