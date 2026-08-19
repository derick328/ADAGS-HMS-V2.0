package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.WriteOffStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewInvoiceWriteOffRequest(@NotNull WriteOffStatus decision, String decisionNote) {
}
