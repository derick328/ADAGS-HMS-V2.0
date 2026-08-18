package com.adags.modules.laboratory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record RecordLabResultRequest(
        @NotNull UUID specimenId,
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
