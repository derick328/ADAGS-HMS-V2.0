package com.adags.modules.inpatient.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record RecordShiftHandoverRequest(
        @NotNull UUID wardId,
        @NotNull UUID shiftFrom,
        UUID shiftTo,
        @NotBlank String situation,
        @NotBlank String background,
        @NotBlank String assessment,
        @NotBlank String recommendation,
        List<String> additionalOutstandingTasks
) {
}
