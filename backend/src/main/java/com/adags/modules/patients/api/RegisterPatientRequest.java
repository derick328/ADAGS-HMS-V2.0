package com.adags.modules.patients.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Either dateOfBirth or ageYears — never both — ⚠3, 05-receptionist.md §4.2.
public record RegisterPatientRequest(
        @NotBlank String givenName,
        String middleName,
        @NotBlank String familyName,
        LocalDate dateOfBirth,
        Integer ageYears,
        Integer ageMonths,
        @NotBlank String gender,
        String maritalStatus,
        @NotBlank String nationalId,
        @NotBlank String phone,
        String email,
        String occupation,
        @NotNull @Valid NextOfKinRequest nextOfKin,
        List<@Valid PatientAddressRequest> addresses,
        @Valid InsurancePolicyRequest insurancePolicy,
        @NotNull UUID facilityId,
        /** Non-null only when staff has confirmed "not the same person" for a §4.1 similarity+DOB block. */
        String notSamePersonConfirmationReason
) {
}
