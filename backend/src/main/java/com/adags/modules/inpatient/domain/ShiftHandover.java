package com.adags.modules.inpatient.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 17-ward-nurse.md §7: "the highest-risk moment in ward nursing," absent
// from the original workflow entirely. SBAR structure — situation,
// background, assessment, recommendation — the standard nurses already
// know. Outgoing nurse records; incoming nurse acknowledges
// (AcknowledgeShiftHandoverService); an unacknowledged handover is flagged
// (idx_shift_handover_unacknowledged, V40).
public record ShiftHandover(
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
        /**
         * §7: "Pre-populated with what the system already knows... carried
         * forward until completed or explicitly closed." Human-readable
         * strings — free text tasks, not a typed model, since a task is as
         * varied as "chase pending lab result" or "reassess wound at 06:00."
         */
        List<String> outstandingTasks,
        Instant createdAt
) {

    public static ShiftHandover record(UUID admissionId, UUID wardId, UUID shiftFrom, UUID shiftTo,
                                        UUID outgoingNurseId, UUID incomingNurseId, String situation,
                                        String background, String assessment, String recommendation,
                                        List<String> outstandingTasks) {
        if (situation == null || situation.isBlank()) {
            throw new IllegalArgumentException("situation is required.");
        }
        if (background == null || background.isBlank()) {
            throw new IllegalArgumentException("background is required.");
        }
        if (assessment == null || assessment.isBlank()) {
            throw new IllegalArgumentException("assessment is required.");
        }
        if (recommendation == null || recommendation.isBlank()) {
            throw new IllegalArgumentException("recommendation is required.");
        }
        return new ShiftHandover(null, admissionId, wardId, shiftFrom, shiftTo, null, outgoingNurseId,
                incomingNurseId, null, null, situation, background, assessment, recommendation,
                outstandingTasks, null);
    }
}
