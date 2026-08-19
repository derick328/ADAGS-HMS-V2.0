package com.adags.modules.finance.api;

import com.adags.modules.finance.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record DisburseBatchRequest(@NotNull PaymentMethod paymentMethod) {
}
