package com.adags.modules.finance.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GeneratePayrollBatchRequest(@Min(1) @Max(12) int month, @Min(2000) int year) {
}
