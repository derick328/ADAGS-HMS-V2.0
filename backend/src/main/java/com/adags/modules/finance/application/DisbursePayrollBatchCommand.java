package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.PaymentMethod;

import java.util.UUID;

public record DisbursePayrollBatchCommand(UUID batchId, UUID disbursedBy, PaymentMethod paymentMethod) {
}
