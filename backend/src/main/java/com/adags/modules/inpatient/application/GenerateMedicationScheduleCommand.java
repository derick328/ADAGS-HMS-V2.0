package com.adags.modules.inpatient.application;

import java.time.Instant;
import java.util.UUID;

public record GenerateMedicationScheduleCommand(
        UUID admissionId, UUID prescriptionLineId, String frequency, int durationDays, Instant startAt
) {
}
