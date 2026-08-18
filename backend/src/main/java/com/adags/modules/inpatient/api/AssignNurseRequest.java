package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.Shift;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record AssignNurseRequest(@NotNull UUID nurseId, @NotNull Shift shift, @NotNull LocalDate assignmentDate) {
}
