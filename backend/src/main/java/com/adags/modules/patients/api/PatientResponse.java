package com.adags.modules.patients.api;

import com.adags.modules.patients.domain.Patient;

import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String hospitalNo,
        String fullName,
        LocalDate dateOfBirth,
        boolean dobIsEstimated,
        String gender,
        String maritalStatus,
        String nationalId,
        String phone,
        String email,
        boolean active
) {
    public static PatientResponse from(Patient patient) {
        return new PatientResponse(
                patient.id(), patient.hospitalNo(), patient.fullName(), patient.dateOfBirth(),
                patient.dobIsEstimated(), patient.gender(), patient.maritalStatus(), patient.nationalId(),
                patient.phone(), patient.email(), patient.active());
    }
}
