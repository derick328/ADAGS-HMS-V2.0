package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WardAlertNotificationRepository {

    WardAlertNotification insert(WardAlertNotification notification);

    List<WardAlertNotification> findByAdmissionId(UUID admissionId);

    /** Unacknowledged notifications for an admission — what shift handover pre-population reads. */
    List<WardAlertNotification> findUnacknowledgedByAdmissionId(UUID admissionId);

    /** Conditional UPDATE ... WHERE acknowledged_at IS NULL — false if already acknowledged. */
    boolean acknowledge(UUID notificationId, UUID acknowledgedBy);

    /** What WardAlertEscalationService's sweep scans — notified before cutoff, still neither acknowledged nor escalated. */
    List<WardAlertNotification> findOverdueUnescalated(Instant cutoff);

    /** Conditional UPDATE ... WHERE escalated_at IS NULL — false if a concurrent sweep already escalated it. */
    boolean escalate(UUID notificationId, UUID escalatedToUserId);
}
