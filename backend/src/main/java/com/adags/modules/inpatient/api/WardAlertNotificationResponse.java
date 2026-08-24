package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.WardAlertNotification;
import com.adags.modules.inpatient.domain.WardAlertSourceType;

import java.time.Instant;
import java.util.UUID;

// Explicit response type, not the domain record returned directly — its
// isAcknowledged()/isEscalated() derived methods make Jackson/springdoc add
// "acknowledged"/"escalated" properties via reflection over declared
// methods, whose order is not guaranteed stable across JVMs/builds and was
// flipping api/openapi.yaml's generated field order between machines.
public record WardAlertNotificationResponse(
        UUID id,
        UUID admissionId,
        WardAlertSourceType sourceType,
        UUID sourceId,
        String detail,
        UUID notifiedUserId,
        Instant notifiedAt,
        Instant acknowledgedAt,
        UUID acknowledgedBy,
        Instant escalatedAt,
        UUID escalatedToUserId,
        Instant createdAt,
        boolean acknowledged,
        boolean escalated
) {
    public static WardAlertNotificationResponse from(WardAlertNotification notification) {
        return new WardAlertNotificationResponse(
                notification.id(), notification.admissionId(), notification.sourceType(), notification.sourceId(),
                notification.detail(), notification.notifiedUserId(), notification.notifiedAt(),
                notification.acknowledgedAt(), notification.acknowledgedBy(), notification.escalatedAt(),
                notification.escalatedToUserId(), notification.createdAt(),
                notification.isAcknowledged(), notification.isEscalated());
    }
}
