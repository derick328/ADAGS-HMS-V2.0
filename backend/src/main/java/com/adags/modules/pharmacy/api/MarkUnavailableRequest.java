package com.adags.modules.pharmacy.api;

import jakarta.validation.constraints.NotBlank;

public record MarkUnavailableRequest(@NotBlank String reason) {
}
