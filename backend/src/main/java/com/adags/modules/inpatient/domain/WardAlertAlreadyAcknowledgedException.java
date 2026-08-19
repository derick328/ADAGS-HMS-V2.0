package com.adags.modules.inpatient.domain;

import java.util.UUID;

public class WardAlertAlreadyAcknowledgedException extends RuntimeException {

    public WardAlertAlreadyAcknowledgedException(UUID notificationId) {
        super("Ward alert notification already acknowledged: " + notificationId);
    }
}
