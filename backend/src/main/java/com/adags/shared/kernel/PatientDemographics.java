package com.adags.shared.kernel;

import java.time.LocalDate;

// Shared/kernel vocabulary (root CLAUDE.md "Modules") so PatientFacade can
// hand another module the two demographic facts vital-range lookup and
// pregnancy-prompt eligibility both need — age band and sex — without
// exposing com.adags.modules.patients.domain.Patient across the boundary.
// gender is the raw patient.gender value (MALE/FEMALE/OTHER/PREFER_NOT_TO_SAY);
// callers needing the vitals-range Sex enum go through Sex.fromPatientGender.
public record PatientDemographics(LocalDate dateOfBirth, String gender) {
}
