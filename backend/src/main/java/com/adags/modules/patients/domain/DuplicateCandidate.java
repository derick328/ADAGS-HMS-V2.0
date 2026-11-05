package com.adags.modules.patients.domain;

import java.util.Set;
import java.util.UUID;

// A candidate may match by national ID, similar full name, or both.
public record DuplicateCandidate(
        UUID patientId,
        String hospitalNo,
        String fullName,
        double nameSimilarity,
        Set<MatchReason> reasons
) {

    public boolean blocks() {
        return reasons.contains(MatchReason.NATIONAL_ID_EXACT)
                || reasons.contains(MatchReason.FULL_NAME_EXACT)
                || reasons.contains(MatchReason.NAME_SIMILARITY);
    }
}
