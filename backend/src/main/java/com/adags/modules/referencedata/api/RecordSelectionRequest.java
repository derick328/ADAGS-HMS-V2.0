package com.adags.modules.referencedata.api;

import jakarta.validation.constraints.NotBlank;

public record RecordSelectionRequest(@NotBlank String selectedCode) {
}
