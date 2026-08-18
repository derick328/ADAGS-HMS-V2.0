package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.application.RecordLabResultResult;
import com.adags.modules.laboratory.domain.VerificationLevel;
import com.adags.modules.laboratory.domain.VerificationState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LabResultResponse(
        UUID id,
        UUID labOrderId,
        UUID specimenId,
        Instant enteredAt,
        Instant releasedAt,
        VerificationState verificationState,
        VerificationLevel verificationLevelApplied,
        UUID amendsId,
        String amendmentReason,
        List<ParameterValueResponse> parameterValues,
        List<UUID> criticalNotificationIds
) {
    public static LabResultResponse from(RecordLabResultResult result) {
        return new LabResultResponse(
                result.result().id(), result.result().labOrderId(), result.result().specimenId(),
                result.result().enteredAt(), result.result().releasedAt(), result.result().verificationState(),
                result.result().verificationLevelApplied(), result.result().amendsId(), result.result().amendmentReason(),
                result.parameterValues().stream().map(ParameterValueResponse::from).toList(),
                result.criticalNotifications().stream().map(n -> n.id()).toList());
    }
}
