package com.adags.modules.clinical.application;

import com.adags.modules.clinical.domain.Diagnosis;
import com.adags.modules.clinical.domain.DiagnosisCertainty;
import com.adags.modules.clinical.domain.DiagnosisRepository;
import com.adags.modules.referencedata.ReferenceDataFacade;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

// 07-doctor.md §4.1. icd10TitleSnapshot is resolved and copied here, at
// diagnosis time, via ReferenceDataFacade — never re-read from the live
// icd10_code table later ("a record written in 2026 must still read as it
// did in 2026"). §4.1's "exactly one may be primary" is enforced by the
// diagnosis table's partial unique index (V14 migration); a caller
// re-marking a new primary is a later item's concern (amending the
// previous primary), not built here.
@Service
public class AddDiagnosisService {

    private final DiagnosisRepository diagnosisRepository;
    private final ReferenceDataFacade referenceDataFacade;
    private final Clock clock;

    public AddDiagnosisService(DiagnosisRepository diagnosisRepository, ReferenceDataFacade referenceDataFacade, Clock clock) {
        this.diagnosisRepository = diagnosisRepository;
        this.referenceDataFacade = referenceDataFacade;
        this.clock = clock;
    }

    public Diagnosis addDiagnosis(
            UUID encounterId, UUID consultationRecordId, String icd10Code, String freeTextClarifier,
            DiagnosisCertainty certainty, boolean isPrimary, UUID diagnosedBy
    ) {
        String titleSnapshot = referenceDataFacade.resolveBillableIcd10Title(icd10Code)
                .orElseThrow(() -> new NoSuchElementException(
                        "No active, billable ICD-10 code: " + icd10Code));

        Diagnosis toInsert = new Diagnosis(null, encounterId, consultationRecordId, icd10Code, titleSnapshot,
                freeTextClarifier, certainty, isPrimary, diagnosedBy, Instant.now(clock), null, null);
        return diagnosisRepository.insert(toInsert);
    }
}
