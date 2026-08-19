package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.RefundStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewRefundRequest(@NotNull RefundStatus decision, String decisionNote) {
}
