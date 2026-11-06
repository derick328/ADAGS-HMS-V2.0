package com.adags.modules.laboratory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AmendLabResultRequest(
        @NotBlank String amendmentReason,
        String methodology,
        String reagentsUsedNote,
        String findings,
        String interpretation,
        String referenceRangeNote,
        String conclusion,
        String machineOutputObjectKey,
        @Valid List<ParameterValueRequest> parameterValues,
        @Valid List<ReagentRequest> reagents,
        String resultType,
        String singleValue,
        String singleValueUnit,
        String overallInterpretation,
        String remarks,
        String labReportObjectKey
) {
    public AmendLabResultRequest(String amendmentReason, String methodology, String reagentsUsedNote,
                                 String findings, String interpretation, String referenceRangeNote,
                                 String conclusion, String machineOutputObjectKey,
                                 List<ParameterValueRequest> parameterValues, List<ReagentRequest> reagents) {
        this(amendmentReason, methodology, reagentsUsedNote, findings, interpretation, referenceRangeNote,
                conclusion, machineOutputObjectKey, parameterValues, reagents, null, null, null, null, null, null);
    }
}
