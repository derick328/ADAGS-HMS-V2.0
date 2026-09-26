package com.adags.modules.patients.api;

import jakarta.validation.constraints.NotBlank;

public record NextOfKinRequest(
        @NotBlank String fullName,
        @NotBlank String relationship,
        String relationshipOther,
        @NotBlank String phone,
        boolean isGuardian
) {
}
