package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 02-billing-architecture.md §6.3/§11. gross = insurancePortion +
// patientPortion is enforced by a DB CHECK (V16), not just here.
//
// coverageRuleId is always null today — no insurance module, no coverage
// rules to attach. authorizationId is always null at creation: nothing in
// Phase 3 item 2 sets it. The gate (item 3) is what may set it, and once
// set it is NEVER cleared (root CLAUDE.md "Billing").
//
// sourceEventType/sourceEventId record which domain event posted this
// line (02-billing-architecture.md §4: "Every charge is posted by a
// domain event. There is no manual charge-entry path.") — e.g.
// ("TriageCompleted", the triage assessment id).
public record InvoiceLine(
        UUID id,
        UUID invoiceId,
        UUID serviceItemId,
        int quantity,
        Money grossAmount,
        Money insurancePortion,
        Money patientPortion,
        UUID coverageRuleId,
        UUID authorizationId,
        String sourceEventType,
        UUID sourceEventId,
        Instant createdAt
) {

    public static InvoiceLine unauthorized(UUID invoiceId, UUID serviceItemId, int quantity, Money grossAmount,
                                            Money insurancePortion, Money patientPortion,
                                            String sourceEventType, UUID sourceEventId) {
        return new InvoiceLine(null, invoiceId, serviceItemId, quantity, grossAmount, insurancePortion,
                patientPortion, null, null, sourceEventType, sourceEventId, null);
    }
}
