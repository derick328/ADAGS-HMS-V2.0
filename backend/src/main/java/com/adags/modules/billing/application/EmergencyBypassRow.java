package com.adags.modules.billing.application;

import java.time.Instant;
import java.util.UUID;

// 02-billing-architecture.md §5.4: one row of the emergency billing bar.
// "Current location" is deliberately absent — it depends on EncounterActivity
// (05a-receptionist-decisions.md §5), a separate cross-role subsystem not
// built yet, not a missing endpoint for this one.
public record EmergencyBypassRow(
        UUID paymentBypassId,
        UUID encounterId,
        UUID patientId,
        String patientName,
        String hospitalNumber,
        Instant grantedAt,
        String reason,
        long outstandingTotalMinor,
        String currencyCode
) {
}
