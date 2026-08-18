package com.adags.modules.patients.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

// verification_status is deliberately absent — a front-desk registration
// always starts UNVERIFIED (matches the insurance_policy table default);
// verifying it is not a receptionist action.
public record InsurancePolicyRequest(
        @NotNull UUID schemeId,
        @NotBlank String policyNumber,
        String memberNumber,
        boolean isPrincipal,
        String principalName,
        LocalDate validFrom,
        LocalDate validTo
) {
}
