package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.SurgeryUrgency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ScheduleSurgeryRequest(
        @NotNull UUID encounterId,
        @NotNull String procedureServiceItemCode,
        @NotNull String surgeryType,
        @NotNull SurgeryUrgency urgency,
        @NotNull String anesthesiaType,
        @NotNull LocalDate scheduledDate,
        @NotNull String timeSlot,
        @NotNull UUID theatreId,
        @Positive int estimatedDurationMinutes,
        List<UUID> assignedNurses,
        String preOpNotes,
        boolean consentObtained,
        String consentDocumentKey
) {
}
