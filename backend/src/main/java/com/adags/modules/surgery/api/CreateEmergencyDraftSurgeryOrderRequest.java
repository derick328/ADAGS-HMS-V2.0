package com.adags.modules.surgery.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateEmergencyDraftSurgeryOrderRequest(
        @NotNull UUID encounterId,
        @NotNull String procedureName,
        @NotNull String surgeryType,
        @NotNull String anesthesiaType,
        @NotNull LocalDate scheduledDate,
        @NotNull String timeSlot,
        @NotNull UUID theatreId,
        @Positive int estimatedDurationMinutes,
        List<UUID> assignedNurses,
        String preOpNotes
) {
}
