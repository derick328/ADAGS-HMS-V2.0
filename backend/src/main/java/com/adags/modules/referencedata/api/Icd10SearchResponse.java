package com.adags.modules.referencedata.api;

import com.adags.modules.referencedata.application.Icd10SearchOutcome;
import com.adags.modules.referencedata.domain.Icd10SearchResult;

import java.util.List;
import java.util.UUID;

public record Icd10SearchResponse(UUID searchLogId, List<Icd10SearchResult> results) {

    public static Icd10SearchResponse from(Icd10SearchOutcome outcome) {
        return new Icd10SearchResponse(outcome.logId(), outcome.results());
    }
}
