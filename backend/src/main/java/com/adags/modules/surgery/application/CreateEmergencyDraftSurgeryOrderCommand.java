package com.adags.modules.surgery.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateEmergencyDraftSurgeryOrderCommand(
        UUID encounterId,
        String procedureName,
        String surgeryType,
        String anesthesiaType,
        LocalDate scheduledDate,
        String timeSlot,
        UUID theatreId,
        int estimatedDurationMinutes,
        List<UUID> assignedNurses,
        String preOpNotes,
        UUID actorUserId
) {
}
