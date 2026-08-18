package com.adags.modules.inventory.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 12-pharmacist.md §10.6, 14-controlled-medicines-tanzania.md §2.1: DATA,
// not code. Seeded empty (V24) and populated only by someone with
// ITEM_CLASSIFY authority, transcribing the regulator's First Schedule —
// never guessed. sourceDocument/sourceDate make each entry traceable to
// where it came from, same discipline as ControlledScheduleEntry in
// 13-controlled-medicines-policy.md §2.1.
//
// atcPrefix is carried for the regulator's data even though no query joins
// on it yet — Item has no ATC code in this build brief's schema, so "match
// on ATC prefix" is honestly inactive rather than silently wrong.
public record NarcoticWatchlistEntry(
        UUID id,
        String innName,
        String atcPrefix,
        String sourceDocument,
        LocalDate sourceDate,
        UUID addedBy,
        Instant addedAt
) {

    public static NarcoticWatchlistEntry add(String innName, String atcPrefix, String sourceDocument,
                                              LocalDate sourceDate, UUID addedBy) {
        if (innName == null || innName.isBlank()) {
            throw new IllegalArgumentException("innName is required.");
        }
        if (sourceDocument == null || sourceDocument.isBlank()) {
            throw new IllegalArgumentException("sourceDocument is required — every entry must be traceable to a source.");
        }
        return new NarcoticWatchlistEntry(null, innName, atcPrefix, sourceDocument, sourceDate, addedBy, null);
    }
}
