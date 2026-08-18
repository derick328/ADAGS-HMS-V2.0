package com.adags.modules.referencedata.domain;

import java.util.List;
import java.util.UUID;

// Split from Icd10CodeRepository on purpose: this is one hand-tuned SQL
// query implementing the §5.2 ranking formula (trigram similarity, common
// list boosts, synonym joins, per-user recency) — a fundamentally different
// shape of query than Icd10CodeRepository's plain CRUD, and worth its own
// seam so the ranking SQL isn't buried in an otherwise-simple repository.
public interface Icd10SearchRepository {

    /**
     * §5.2's ranking, applied over codes that are is_active AND is_billable
     * and match on code prefix, title, an approved synonym, or trigram
     * similarity. departmentId/actorUserId may be null (no department
     * common-list boost / no recency boost respectively). Capped at limit.
     */
    List<Icd10SearchResult> search(String queryText, UUID departmentId, UUID actorUserId, int limit);
}
