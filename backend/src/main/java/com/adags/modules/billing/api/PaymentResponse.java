package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.Payment;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.billing.domain.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID patientId,
        UUID cashierShiftId,
        long amountMinor,
        String currencyCode,
        PaymentMethod method,
        String reference,
        PaymentStatus status,
        Instant receivedAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.id(), payment.patientId(), payment.cashierShiftId(),
                payment.amount().amountMinor(), payment.amount().currencyCode(), payment.method(),
                payment.reference(), payment.status(), payment.receivedAt());
    }
}
