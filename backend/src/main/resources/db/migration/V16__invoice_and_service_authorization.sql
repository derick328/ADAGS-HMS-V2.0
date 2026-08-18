-- Phase 3, billing gate, item 2: Invoice, InvoiceLine, ServiceAuthorization.
-- 02-billing-architecture.md §3.2, §6.3, §10, §11; root CLAUDE.md "Billing".
--
-- invoice_line.authorization_id and service_authorization.invoice_line_id
-- reference each other, so invoice_line is created first without the FK
-- on authorization_id, service_authorization is created referencing
-- invoice_line, and the FK is added back with ALTER TABLE. This is the
-- standard technique for a genuine circular reference between two tables.

CREATE TABLE invoice (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Nullable: a pharmacy walk-in or a records fee has no encounter
    -- (02-billing-architecture.md §11).
    encounter_id                   UUID REFERENCES encounter(id),
    patient_id                     UUID NOT NULL REFERENCES patient(id),
    -- root CLAUDE.md "Facility": an invoice happened at a place.
    facility_id                    UUID NOT NULL REFERENCES facility(id),
    state                          TEXT NOT NULL DEFAULT 'DRAFT' CHECK (state IN (
                                       'DRAFT', 'ISSUED', 'PATIENT_PORTION_SETTLED', 'CLOSED',
                                       'CANCELLATION_PENDING', 'VOID', 'RECEIVABLE_POST_DISCHARGE'
                                   )),
    -- Pricing-dimension payer category (billing.domain.PayerCategory), not
    -- shared.kernel.PayerType — see V15's comment on price_list.
    payer_category                 TEXT NOT NULL CHECK (payer_category IN (
                                       'CASH', 'NHIF', 'PRIVATE_SCHEME', 'CORPORATE', 'STAFF'
                                   )),
    -- No FK: the insurance module doesn't exist yet.
    insurance_policy_id            UUID,
    -- Null until ISSUED — assigned from the gapless per-facility per-year
    -- sequence at issue, never at draft (§10).
    invoice_number                 TEXT UNIQUE,
    gross_total_minor              BIGINT NOT NULL DEFAULT 0 CHECK (gross_total_minor >= 0),
    insurance_portion_total_minor  BIGINT NOT NULL DEFAULT 0 CHECK (insurance_portion_total_minor >= 0),
    patient_portion_total_minor    BIGINT NOT NULL DEFAULT 0 CHECK (patient_portion_total_minor >= 0),
    currency_code                  TEXT NOT NULL DEFAULT 'TZS',
    -- backend/CLAUDE.md: "Optimistic locking (@Version) on ... Invoice."
    version                        INT NOT NULL DEFAULT 0,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                     UUID NOT NULL REFERENCES app_user(id),
    CHECK (gross_total_minor = insurance_portion_total_minor + patient_portion_total_minor),
    -- A number exists for every state except DRAFT — the two-step issue's
    -- DB-level guarantee, independent of application code remembering it.
    CHECK (state = 'DRAFT' OR invoice_number IS NOT NULL)
);

CREATE INDEX idx_invoice_encounter ON invoice (encounter_id);

CREATE TABLE invoice_line (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id                UUID NOT NULL REFERENCES invoice(id),
    service_item_id           UUID NOT NULL REFERENCES service_item(id),
    quantity                  INT NOT NULL CHECK (quantity > 0),
    gross_minor               BIGINT NOT NULL CHECK (gross_minor >= 0),
    insurance_portion_minor   BIGINT NOT NULL DEFAULT 0 CHECK (insurance_portion_minor >= 0),
    patient_portion_minor     BIGINT NOT NULL CHECK (patient_portion_minor >= 0),
    currency_code             TEXT NOT NULL DEFAULT 'TZS',
    -- No FK: which SchemeCoverageRule produced the split — insurance
    -- module doesn't exist yet (§6.3).
    coverage_rule_id          UUID,
    -- FK added below, after service_authorization exists (circular ref).
    authorization_id          UUID,
    -- Which domain event posted this line, e.g. ('TriageCompleted', the
    -- triage assessment id) — §4: "Every charge is posted by a domain
    -- event. There is no manual charge-entry path."
    source_event_type         TEXT NOT NULL,
    source_event_id           UUID NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (gross_minor = insurance_portion_minor + patient_portion_minor)
);

CREATE INDEX idx_invoice_line_invoice ON invoice_line (invoice_id);

CREATE TABLE service_authorization (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_line_id     UUID NOT NULL UNIQUE REFERENCES invoice_line(id),
    authorization_type  TEXT NOT NULL CHECK (authorization_type IN (
                            'PAYMENT', 'INSURANCE', 'EMERGENCY_BYPASS', 'WAIVER', 'DEPOSIT'
                        )),
    authorized_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    authorized_by       UUID NOT NULL REFERENCES app_user(id),
    -- Payment, claim, bypass or deposit-account id depending on
    -- authorization_type — no single table to FK against.
    reference_id        UUID NOT NULL
);

ALTER TABLE invoice_line
    ADD CONSTRAINT fk_invoice_line_authorization FOREIGN KEY (authorization_id) REFERENCES service_authorization(id);
