package com.adags.modules.patients.domain;

import java.time.LocalDate;

public record DuplicateCheckQuery(
        String givenName,
        String familyName,
        LocalDate dateOfBirth,
        String nationalId,
        String phone,
        double lowSimilarityThreshold,
        double highSimilarityThreshold
) {
}
