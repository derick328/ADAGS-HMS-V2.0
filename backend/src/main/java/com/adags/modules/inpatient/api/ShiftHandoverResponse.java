package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.ShiftHandover;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShiftHandoverResponse(
        UUID id,
        UUID admissionId,
        UUID wardId,
        UUID shiftFrom,
        UUID shiftTo,
        Instant handoverAt,
        UUID outgoingNurseId,
        UUID incomingNurseId,
        Instant acknowledgedAt,
        UUID acknowledgedBy,
        String situation,
        String background,
        String assessment,
        String recommendation,
        List<String> outstandingTasks
) {
    public static ShiftHandoverResponse from(ShiftHandover handover) {
        return new ShiftHandoverResponse(handover.id(), handover.admissionId(), handover.wardId(),
                handover.shiftFrom(), handover.shiftTo(), handover.handoverAt(), handover.outgoingNurseId(),
                handover.incomingNurseId(), handover.acknowledgedAt(), handover.acknowledgedBy(),
                handover.situation(), handover.background(), handover.assessment(), handover.recommendation(),
                handover.outstandingTasks());
    }
}
