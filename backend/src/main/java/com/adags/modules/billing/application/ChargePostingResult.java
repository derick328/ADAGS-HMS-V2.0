package com.adags.modules.billing.application;

import com.adags.shared.kernel.Money;

import java.util.UUID;

public record ChargePostingResult(
        UUID invoiceId,
        String invoiceNumber,
        UUID invoiceLineId,
        Money grossAmount,
        Money patientPortion
) {
}
