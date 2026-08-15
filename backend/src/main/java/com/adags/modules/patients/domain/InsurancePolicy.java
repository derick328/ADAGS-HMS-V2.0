package com.adags.modules.patients.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InsurancePolicy(
        UUID id,
        UUID patientId,
        UUID schemeId,
        String policyNumber,
        String memberNumber,
        boolean isPrincipal,
        String principalName,
        LocalDate validFrom,
        LocalDate validTo,
        String verificationStatus,
        Instant verifiedAt
) {

    public boolean isValidOn(LocalDate date) {
        boolean afterStart = validFrom == null || !date.isBefore(validFrom);
        boolean beforeEnd = validTo == null || !date.isAfter(validTo);
        return afterStart && beforeEnd;
    }
}
