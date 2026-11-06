package com.adags.modules.laboratory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

// parameterValues is @Valid but NOT @NotEmpty: a lab_test with zero coded
// lab_test_parameter rows (V72 backfill + any test the lab lead hasn't
// broken into parameters yet) is legitimately note-only — the technician
// releases with findings/interpretation/conclusion prose only, no
// numeric rows. RecordLabResultService handles an empty list already
// (insertParameterValues loops zero times, raiseCriticalNotifications
// finds nothing to flag). Enforcing at-least-one row here rejected
// every such release with a bare "Validation failed" and the
// technician had no way past it.
public record RecordLabResultRequest(
        @NotNull UUID specimenId,
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
    public RecordLabResultRequest(UUID specimenId, String methodology, String reagentsUsedNote,
                                  String findings, String interpretation, String referenceRangeNote,
                                  String conclusion, String machineOutputObjectKey,
                                  List<ParameterValueRequest> parameterValues, List<ReagentRequest> reagents) {
        this(specimenId, methodology, reagentsUsedNote, findings, interpretation, referenceRangeNote,
                conclusion, machineOutputObjectKey, parameterValues, reagents, null, null, null, null, null, null);
    }
}
