package com.adags.modules.patients.api;

import jakarta.validation.constraints.NotBlank;

public record NextOfKinRequest(
        @NotBlank String fullName,
        @NotBlank String relationship,
        @NotBlank String phone,
        boolean isGuardian
) {
}
