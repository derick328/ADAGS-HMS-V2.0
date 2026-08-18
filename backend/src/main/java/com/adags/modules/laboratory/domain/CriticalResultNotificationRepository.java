package com.adags.modules.laboratory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CriticalResultNotificationRepository {

    CriticalResultNotification insert(CriticalResultNotification notification);

    Optional<CriticalResultNotification> findById(UUID id);

    List<CriticalResultNotification> findByResultId(UUID resultId);

    /**
     * Sets acknowledgedAt/acknowledgedBy — only while unacknowledged.
     * Returns false (a no-op) if already acknowledged.
     */
    boolean acknowledge(UUID id, UUID acknowledgedBy);

    /** Unacknowledged, unescalated, notified before the cutoff — what a sweep escalates. */
    List<CriticalResultNotification> findOverdueUnescalated(Instant cutoff);

    /**
     * Sets escalatedAt/escalatedToUserId — only while unescalated.
     * Returns false (a no-op) if already escalated (or since-acknowledged).
     */
    boolean escalate(UUID id, UUID escalatedToUserId);
}
