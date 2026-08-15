package com.adags.modules.patients.domain;

import java.time.LocalDate;
import java.util.UUID;

public record Patient(
        UUID id,
        String hospitalNo,
        String givenName,
        String middleName,
        String familyName,
        LocalDate dateOfBirth,
        boolean dobIsEstimated,
        String gender,
        String maritalStatus,
        String nationalId,
        String phone,
        String email,
        String occupation,
        boolean identityProvisional,
        boolean active,
        UUID mergedIntoId
) {

    public boolean isMerged() {
        return mergedIntoId != null;
    }

    public String fullName() {
        return middleName == null || middleName.isBlank()
                ? givenName + " " + familyName
                : givenName + " " + middleName + " " + familyName;
    }
}
