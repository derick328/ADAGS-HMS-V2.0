package com.adags.modules.laboratory.application;

import java.util.List;
import java.util.UUID;

public record RecordLabResultCommand(
        UUID specimenId,
        UUID enteredBy,
        String methodology,
        String reagentsUsedNote,
        String findings,
        String interpretation,
        String referenceRangeNote,
        String conclusion,
        String machineOutputObjectKey,
        List<ParameterEntry> parameterValues,
        List<ReagentEntry> reagents,
        String resultType,
        String singleValue,
        String singleValueUnit,
        String overallInterpretation,
        String remarks,
        String labReportObjectKey
) {
    public RecordLabResultCommand(UUID specimenId, UUID enteredBy, String methodology, String reagentsUsedNote,
                                  String findings, String interpretation, String referenceRangeNote,
                                  String conclusion, String machineOutputObjectKey,
                                  List<ParameterEntry> parameterValues, List<ReagentEntry> reagents) {
        this(specimenId, enteredBy, methodology, reagentsUsedNote, findings, interpretation, referenceRangeNote,
                conclusion, machineOutputObjectKey, parameterValues, reagents, null, null, null, null, null, null);
    }
}
