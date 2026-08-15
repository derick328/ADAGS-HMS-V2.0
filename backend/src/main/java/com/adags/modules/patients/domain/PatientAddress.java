package com.adags.modules.patients.domain;

import java.util.UUID;

public record PatientAddress(
        UUID id,
        UUID patientId,
        String addressType,
        String region,
        String district,
        String ward,
        String streetOrVillage,
        String landmark
) {
}
