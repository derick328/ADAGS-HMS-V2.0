package com.adags.modules.laboratory.domain;

import java.time.Instant;
import java.util.UUID;

// 15-lab-technician.md §7: one row per critical parameter on a released
// result. This row IS the notification record — notifiedAt is stamped in
// the same transaction as release, so "immediate" means no delay between
// release and this row existing (no push channel exists yet to actually
// deliver it — CriticalResultEscalationService's sweep is what makes the
// escalation half of this real).
public record CriticalResultNotification(
        UUID id,
        UUID resultId,
        UUID parameterId,
        double value,
        UUID notifiedUserId,
        Instant notifiedAt,
        Instant acknowledgedAt,
        UUID acknowledgedBy,
        Instant escalatedAt,
        UUID escalatedToUserId,
        Instant createdAt
) {

    public static CriticalResultNotification raise(UUID resultId, UUID parameterId, double value, UUID notifiedUserId) {
        return new CriticalResultNotification(
                null, resultId, parameterId, value, notifiedUserId, null, null, null, null, null, null);
    }

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }

    public boolean isEscalated() {
        return escalatedAt != null;
    }
}
