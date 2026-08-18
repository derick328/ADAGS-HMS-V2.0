package com.adags.modules.patients.api;

import jakarta.validation.constraints.NotBlank;

public record PatientAddressRequest(
        @NotBlank String addressType,
        String region,
        String district,
        String ward,
        String streetOrVillage,
        String landmark
) {
}
