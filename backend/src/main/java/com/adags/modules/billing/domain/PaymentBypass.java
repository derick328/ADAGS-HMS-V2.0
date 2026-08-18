package com.adags.modules.billing.domain;

import java.time.Instant;
import java.util.UUID;

// root CLAUDE.md "Billing": "An active PaymentBypass satisfies the gate.
// IMMEDIATE triage priority creates one automatically" and "scope is the
// whole encounter, not one charge" (02-billing-architecture.md §5.1) —
// hence encounterId, not an invoice-line-level reference.
public record PaymentBypass(
        UUID id,
        UUID encounterId,
        PaymentBypassSource source,
        String reason,
        PaymentBypassStatus status,
        UUID grantedBy,
        Instant grantedAt,
        UUID revokedBy,
        Instant revokedAt
) {

    public static PaymentBypass grant(UUID encounterId, PaymentBypassSource source, String reason, UUID grantedBy) {
        return new PaymentBypass(null, encounterId, source, reason, PaymentBypassStatus.ACTIVE,
                grantedBy, null, null, null);
    }
}
