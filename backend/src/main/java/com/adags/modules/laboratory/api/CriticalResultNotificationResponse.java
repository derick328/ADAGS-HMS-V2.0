package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.CriticalResultNotification;

import java.time.Instant;
import java.util.UUID;

public record CriticalResultNotificationResponse(
        UUID id,
        UUID parameterId,
        double value,
        UUID notifiedUserId,
        Instant notifiedAt,
        Instant acknowledgedAt,
        Instant escalatedAt
) {
    public static CriticalResultNotificationResponse from(CriticalResultNotification notification) {
        return new CriticalResultNotificationResponse(notification.id(), notification.parameterId(),
                notification.value(), notification.notifiedUserId(), notification.notifiedAt(),
                notification.acknowledgedAt(), notification.escalatedAt());
    }
}
