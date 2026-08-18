package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.domain.SubstitutionStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewSubstitutionRequest(@NotNull SubstitutionStatus decision, String reviewNote) {
}
