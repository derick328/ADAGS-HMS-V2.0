package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.VerificationLevel;
import com.adags.modules.laboratory.domain.VerificationState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 15-lab-technician.md §9/§9.1: the My History detail/print view — every
// field the printable report must carry (patient identity, accession,
// specimen, every parameter with value/unit/reference range/flag,
// methodology, entering/verifying technicians, release timestamp, and the
// amendment-supersession notice where amendsId is set).
public record LabResultDetailResponse(
        UUID id,
        UUID labOrderId,
        UUID specimenId,
        String accessionNo,
        String specimenType,
        String patientName,
        String hospitalNumber,
        String labTestCode,
        String labTestName,
        String clinicalIndication,
        String methodology,
        String reagentsUsedNote,
        String findings,
        String interpretation,
        String referenceRangeNote,
        String conclusion,
        String machineOutputObjectKey,
        String enteredByName,
        Instant enteredAt,
        Instant releasedAt,
        VerificationState verificationState,
        VerificationLevel verificationLevelApplied,
        String verifiedByName,
        Instant verifiedAt,
        UUID amendsId,
        Instant amendsOriginalEnteredAt,
        String amendmentReason,
        List<DetailParameterValueResponse> parameterValues,
        String resultType,
        String singleValue,
        String singleValueUnit,
        String overallInterpretation,
        String remarks,
        String labReportObjectKey
) {
    public record DetailParameterValueResponse(
            UUID parameterId,
            String parameterName,
            String unit,
            String method,
            double resultValue,
            String flag,
            boolean flagOverridden,
            String overrideReason,
            Double normalLow,
            Double normalHigh,
            Double criticalLow,
            Double criticalHigh
    ) {
    }
}
