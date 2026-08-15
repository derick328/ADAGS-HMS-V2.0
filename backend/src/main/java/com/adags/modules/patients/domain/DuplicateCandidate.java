package com.adags.modules.patients.domain;

import java.util.Set;
import java.util.UUID;

// 05-receptionist.md §4.1's three tiers, collapsed onto one candidate: a
// name-similarity match on the same row as a DOB match is a stronger signal
// than either alone, so reasons accumulate rather than replace each other.
public record DuplicateCandidate(
        UUID patientId,
        String hospitalNo,
        String fullName,
        double nameSimilarity,
        Set<MatchReason> reasons
) {

    public boolean blocks() {
        return reasons.contains(MatchReason.NATIONAL_ID_EXACT)
                || reasons.contains(MatchReason.NAME_SIMILARITY_WITH_DOB_MATCH);
    }

    public boolean blocksUnconditionally() {
        return reasons.contains(MatchReason.NATIONAL_ID_EXACT);
    }
}
