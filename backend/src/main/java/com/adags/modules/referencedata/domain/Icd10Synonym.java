package com.adags.modules.referencedata.domain;

import java.time.Instant;
import java.util.UUID;

// 10-clinical-reference-data.md §2.3/§3. "An incorrect synonym silently
// produces wrong diagnoses" — the review fields (reviewedBy/reviewedAt/
// reviewNote) exist because that failure mode has no other visible trace;
// the audit trail IS the safety control. proposedBy != reviewedBy is
// enforced at the database (V13 migration CHECK), the same
// second-person-constraint pattern as elevation_request.reviewed_by !=
// requested_by (root CLAUDE.md "Authorisation").
public record Icd10Synonym(
        UUID id,
        String code,
        String term,
        String language,
        SynonymStatus status,
        UUID proposedBy,
        Instant proposedAt,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        boolean isActive
) {

    public static Icd10Synonym propose(String code, String term, String language, UUID proposedBy) {
        return new Icd10Synonym(null, code, term, language, SynonymStatus.PROPOSED,
                proposedBy, null, null, null, null, true);
    }
}
