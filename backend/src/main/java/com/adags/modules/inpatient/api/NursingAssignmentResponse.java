package com.adags.modules.inpatient.api;

import com.adags.modules.inpatient.domain.NursingAssignment;
import com.adags.modules.inpatient.domain.Shift;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NursingAssignmentResponse(
        UUID id, UUID admissionId, UUID nurseId, Shift shift, LocalDate assignmentDate, UUID assignedBy, Instant createdAt
) {
    public static NursingAssignmentResponse from(NursingAssignment assignment) {
        return new NursingAssignmentResponse(assignment.id(), assignment.admissionId(), assignment.nurseId(),
                assignment.shift(), assignment.assignmentDate(), assignment.assignedBy(), assignment.createdAt());
    }
}
