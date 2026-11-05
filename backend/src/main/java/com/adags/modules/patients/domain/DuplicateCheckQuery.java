package com.adags.modules.patients.domain;

public record DuplicateCheckQuery(
        String givenName,
        String middleName,
        String familyName,
        String nationalId,
        double highSimilarityThreshold
) {
}
