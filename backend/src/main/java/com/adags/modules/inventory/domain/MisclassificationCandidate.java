package com.adags.modules.inventory.domain;

import java.util.UUID;

// 12-pharmacist.md §10.6 detection report row: an UNCLASSIFIED or
// PRESCRIPTION_ONLY item whose name trigram-matches a narcotic watchlist
// entry. False positives are expected and cheap; a missed controlled drug
// is not (13-*.md §6.1).
public record MisclassificationCandidate(
        UUID itemId,
        String itemCode,
        String itemName,
        RegulatorySchedule regulatorySchedule,
        String matchedInnName,
        double similarityScore
) {
}
