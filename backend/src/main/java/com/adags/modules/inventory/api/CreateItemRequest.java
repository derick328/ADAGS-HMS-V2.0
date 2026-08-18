package com.adags.modules.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateItemRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String classification,
        @NotBlank String form,
        String strength,
        @NotBlank String unit,
        @Min(0) int reorderLevel
) {
}
