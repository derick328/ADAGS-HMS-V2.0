package com.adags.modules.billing.api;

import com.adags.modules.billing.application.EmergencyBypassRow;

import java.time.Instant;
import java.util.UUID;

public record EmergencyBypassResponse(
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

    public static EmergencyBypassResponse from(EmergencyBypassRow row) {
        return new EmergencyBypassResponse(row.paymentBypassId(), row.encounterId(), row.patientId(),
                row.patientName(), row.hospitalNumber(), row.grantedAt(), row.reason(),
                row.outstandingTotalMinor(), row.currencyCode());
    }
}
