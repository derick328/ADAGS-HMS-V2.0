package com.adags.modules.laboratory.domain;

import java.util.UUID;

public class CriticalNotificationAlreadyAcknowledgedException extends RuntimeException {

    public CriticalNotificationAlreadyAcknowledgedException(UUID notificationId) {
        super("Critical result notification " + notificationId + " was already acknowledged.");
    }
}
