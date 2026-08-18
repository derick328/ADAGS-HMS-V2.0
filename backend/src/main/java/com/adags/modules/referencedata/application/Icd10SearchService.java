package com.adags.modules.referencedata.application;

import com.adags.modules.referencedata.domain.CatalogueType;
import com.adags.modules.referencedata.domain.Icd10SearchRepository;
import com.adags.modules.referencedata.domain.Icd10SearchResult;
import com.adags.modules.referencedata.domain.ReferenceSearchLog;
import com.adags.modules.referencedata.domain.ReferenceSearchLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// 10-clinical-reference-data.md §5.3's performance targets (min 2
// characters, 20-result cap; the 200ms debounce is a client concern) and
// §5.4: every search is logged, unconditionally — that log is what makes
// the reference layer self-improving (§4's suggestions panel, missing-
// synonym detection), so it can't be an opt-in the caller forgets.
@Service
public class Icd10SearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int RESULT_CAP = 20;

    private final Icd10SearchRepository searchRepository;
    private final ReferenceSearchLogRepository logRepository;

    public Icd10SearchService(Icd10SearchRepository searchRepository, ReferenceSearchLogRepository logRepository) {
        this.searchRepository = searchRepository;
        this.logRepository = logRepository;
    }

    public Icd10SearchOutcome search(String queryText, UUID departmentId, UUID actorUserId) {
        if (queryText == null || queryText.trim().length() < MIN_QUERY_LENGTH) {
            throw new IllegalArgumentException("Search query must be at least " + MIN_QUERY_LENGTH + " characters.");
        }
        String trimmed = queryText.trim();
        List<Icd10SearchResult> results = searchRepository.search(trimmed, departmentId, actorUserId, RESULT_CAP);

        ReferenceSearchLog log = logRepository.insert(new ReferenceSearchLog(
                null, actorUserId, departmentId, CatalogueType.ICD10, trimmed, results.size(), null, null));

        return new Icd10SearchOutcome(log.id(), results);
    }

    /** Reported after the fact, once the clinician actually picks a result from {@link #search}'s log row. */
    public void recordSelection(UUID logId, String selectedCode) {
        logRepository.recordSelection(logId, selectedCode);
    }
}
