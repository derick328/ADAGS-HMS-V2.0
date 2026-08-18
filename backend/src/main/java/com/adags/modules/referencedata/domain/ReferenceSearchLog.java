package com.adags.modules.referencedata.domain;

import java.time.Instant;
import java.util.UUID;

// 10-clinical-reference-data.md §5.4: "the mechanism that lets the
// reference layer improve itself" — feeds the common-list suggestions
// (§4), identifies missing synonyms (zero-result or unselected searches),
// and shows which common lists are working. selectedId is TEXT rather than
// a typed FK because it means different things per catalogueType (an
// icd10_code's code today; a UUID-shaped id for the SERVICE_ITEM/ITEM
// catalogues §2.4 anticipates) — recorded after the fact via
// recordSelection, so it starts null on every row.
public record ReferenceSearchLog(
        UUID id,
        UUID actorUserId,
        UUID departmentId,
        CatalogueType catalogueType,
        String queryText,
        int resultCount,
        String selectedId,
        Instant searchedAt
) {
}
