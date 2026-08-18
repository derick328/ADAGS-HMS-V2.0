package com.adags.modules.inpatient.api;

import jakarta.validation.constraints.NotNull;

public record CreateBedRequest(@NotNull String label, @NotNull String bedType) {
}
