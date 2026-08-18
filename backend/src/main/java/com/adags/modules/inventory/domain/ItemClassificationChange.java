package com.adags.modules.inventory.domain;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §10.6, 13-controlled-medicines-policy.md §2/§6.2:
// permanent, append-only audit trail of every regulatory_schedule change —
// actor, timestamp, previous value, reason. CONTROLLED -> PRESCRIPTION_ONLY
// is "the transition that needs scrutiny" (build brief item 4); every
// other transition is recorded the same way, just not singled out by the
// detection report.
public record ItemClassificationChange(
        UUID id,
        UUID itemId,
        RegulatorySchedule previousSchedule,
        RegulatorySchedule newSchedule,
        String reason,
        UUID changedBy,
        Instant changedAt
) {

    public static ItemClassificationChange record(UUID itemId, RegulatorySchedule previousSchedule,
                                                    RegulatorySchedule newSchedule, String reason, UUID changedBy) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required for a classification change.");
        }
        return new ItemClassificationChange(null, itemId, previousSchedule, newSchedule, reason, changedBy, null);
    }

    public boolean isControlledRelease() {
        return previousSchedule == RegulatorySchedule.CONTROLLED && newSchedule == RegulatorySchedule.PRESCRIPTION_ONLY;
    }
}
