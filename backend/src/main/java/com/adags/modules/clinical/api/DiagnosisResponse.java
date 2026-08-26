package com.adags.modules.clinical.api;

import com.adags.modules.clinical.domain.Diagnosis;
import com.adags.modules.clinical.domain.DiagnosisCertainty;

import java.time.Instant;
import java.util.UUID;

public record DiagnosisResponse(
        UUID id,
        UUID encounterId,
        UUID consultationRecordId,
        String icd10Code,
        String icd10TitleSnapshot,
        String freeTextClarifier,
        DiagnosisCertainty certainty,
        boolean isPrimary,
        UUID diagnosedBy,
        Instant diagnosedAt
) {
    public static DiagnosisResponse from(Diagnosis diagnosis) {
        return new DiagnosisResponse(
                diagnosis.id(), diagnosis.encounterId(), diagnosis.consultationRecordId(), diagnosis.icd10Code(),
                diagnosis.icd10TitleSnapshot(), diagnosis.freeTextClarifier(), diagnosis.certainty(),
                diagnosis.isPrimary(), diagnosis.diagnosedBy(), diagnosis.diagnosedAt());
    }
}
