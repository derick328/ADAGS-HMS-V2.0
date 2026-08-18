package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.RegulatorySchedule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClassifyItemRequest(@NotNull RegulatorySchedule regulatorySchedule, @NotBlank String reason) {
}
