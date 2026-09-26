package com.adags.modules.patients.domain;

import java.util.UUID;

public record NextOfKin(
        UUID id,
        UUID patientId,
        String fullName,
        String relationship,
        String relationshipOther,
        String phone,
        boolean isGuardian
) {
}
