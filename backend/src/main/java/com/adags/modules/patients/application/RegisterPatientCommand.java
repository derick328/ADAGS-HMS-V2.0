package com.adags.modules.patients.application;

import com.adags.modules.patients.domain.InsurancePolicy;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.PatientAddress;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Either dateOfBirth or ageYears must be set, never both — ⚠3, 05-receptionist.md
// §4.2. nextOfKin, addresses and insurancePolicy carry a null patientId; the
// service fills it in once the Patient row exists.
public record RegisterPatientCommand(
        String givenName,
        String middleName,
        String familyName,
        LocalDate dateOfBirth,
        Integer ageYears,
        Integer ageMonths,
        String gender,
        String maritalStatus,
        String nationalId,
        String phone,
        String email,
        String occupation,
        NextOfKin nextOfKin,
        List<PatientAddress> addresses,
        InsurancePolicy insurancePolicy,
        UUID facilityId,
        UUID actorUserId,
        /** Non-null only when staff has explicitly confirmed "not the same person" for a §4.1 similarity+DOB block. */
        String notSamePersonConfirmationReason
) {
}
