package com.adags.modules.laboratory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

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
        @NotEmpty @Valid List<ParameterValueRequest> parameterValues,
        @Valid List<ReagentRequest> reagents
) {
}
