package com.adags.modules.finance.api;

import jakarta.validation.constraints.NotBlank;

public record VoidExpenseRequest(@NotBlank String voidReason) {
}
