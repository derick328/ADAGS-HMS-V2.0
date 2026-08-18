package com.adags.modules.referencedata.application;

import com.adags.modules.referencedata.domain.Icd10CodeRepository;
import com.adags.modules.referencedata.domain.Icd10Synonym;
import com.adags.modules.referencedata.domain.Icd10SynonymRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

// 10-clinical-reference-data.md §3: "Any clinician -> proposes a synonym
// (PROPOSED)" — REFERENCE_SYNONYM_PROPOSE-gated at the controller. Free to
// propose; §2.3's language set is validated here with a clear message
// rather than left to the DB CHECK's generic constraint-violation error.
@Service
public class ProposeIcd10SynonymService {

    private static final Set<String> VALID_LANGUAGES = Set.of("sw", "en", "local");

    private final Icd10SynonymRepository synonymRepository;
    private final Icd10CodeRepository codeRepository;

    public ProposeIcd10SynonymService(Icd10SynonymRepository synonymRepository, Icd10CodeRepository codeRepository) {
        this.synonymRepository = synonymRepository;
        this.codeRepository = codeRepository;
    }

    public Icd10Synonym propose(String code, String term, String language, UUID proposedBy) {
        codeRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("ICD-10 code not found: " + code));
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("term is required.");
        }
        if (!VALID_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("language must be one of " + VALID_LANGUAGES + ", not " + language + ".");
        }
        return synonymRepository.insert(Icd10Synonym.propose(code, term.trim(), language, proposedBy));
    }
}
