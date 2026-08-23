package com.adags.modules.billing.api;

import jakarta.validation.constraints.NotNull;

public record IssueInvoiceRequest(@NotNull Integer expectedVersion) {
}
