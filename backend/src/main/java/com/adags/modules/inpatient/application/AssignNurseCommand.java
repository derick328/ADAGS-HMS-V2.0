package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.Shift;

import java.time.LocalDate;
import java.util.UUID;

public record AssignNurseCommand(UUID admissionId, UUID nurseId, Shift shift, LocalDate assignmentDate, UUID assignedBy) {
}
