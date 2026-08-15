package com.adags.modules.patients.domain;

import java.util.UUID;

public record PatientIdentifier(
        UUID id,
        UUID patientId,
        String identifierType,
        String value,
        String issuingAuthority
) {
}
