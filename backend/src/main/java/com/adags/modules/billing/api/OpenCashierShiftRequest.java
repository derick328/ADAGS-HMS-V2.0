package com.adags.modules.billing.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenCashierShiftRequest(@NotNull UUID facilityId) {
}
