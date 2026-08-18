package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.MisclassificationCandidate;
import com.adags.modules.inventory.domain.RegulatorySchedule;

import java.util.UUID;

public record MisclassificationCandidateResponse(
        UUID itemId, String itemCode, String itemName, RegulatorySchedule regulatorySchedule,
        String matchedInnName, double similarityScore
) {

    public static MisclassificationCandidateResponse from(MisclassificationCandidate candidate) {
        return new MisclassificationCandidateResponse(candidate.itemId(), candidate.itemCode(), candidate.itemName(),
                candidate.regulatorySchedule(), candidate.matchedInnName(), candidate.similarityScore());
    }
}
