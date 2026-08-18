package com.adags.modules.inpatient.api;

import jakarta.validation.constraints.NotBlank;

public record ReverseDischargeOrderRequest(@NotBlank String reason) {
}
