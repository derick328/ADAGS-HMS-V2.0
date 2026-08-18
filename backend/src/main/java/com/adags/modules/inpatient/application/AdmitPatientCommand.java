package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.WardClass;

import java.util.UUID;

public record AdmitPatientCommand(
        UUID encounterId,
        UUID admittingDoctorId,
        WardClass orderedWardClass,
        boolean retrospectiveCompletion,
        String retrospectiveNote
) {
}
