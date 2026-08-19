package com.adags.modules.patients;

import com.adags.shared.kernel.PatientDemographics;
import com.adags.shared.kernel.PatientIdentity;
import com.adags.shared.kernel.PayerType;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). Returns shared/kernel vocabulary, never a patients-module
// domain type — encounters must not import com.adags.modules.patients.domain.
public interface PatientFacade {

    boolean exists(UUID patientId);

    /** Active insurance policy on {@code onDate}, else CASH — 05-receptionist.md §5.2 step 2. */
    PayerType resolvePayerType(UUID patientId, LocalDate onDate);

    /**
     * The scheme of the patient's active insurance policy on
     * {@code onDate}, if any — Phase 7 item 3: the coverage-rule split
     * needs the scheme id itself, not just the coarse CASH/INSURANCE
     * PayerType resolvePayerType already gives. Same "active" definition
     * as resolvePayerType (InsurancePolicy.isValidOn), not verification
     * status — verifying the policy is the billing gate's concern
     * (BillingGate / a later part), not this split.
     *
     * ⚠ If a patient holds more than one active policy, the first found
     * wins — no stated precedence exists yet (e.g. principal over
     * dependent, or newest over oldest). Revisit if that ever matters in
     * practice.
     */
    Optional<UUID> resolveActiveInsuranceSchemeId(UUID patientId, LocalDate onDate);

    /**
     * Phase 7 item 3 part 3: true when the patient holds an active
     * policy on {@code onDate} AND that policy's verification_status is
     * VERIFIED. This is the check resolveActiveInsuranceSchemeId
     * deliberately skips (the split doesn't need verification) — the
     * billing gate's coverage clause does: an unverified policy must not
     * let a patient through for free.
     */
    boolean hasVerifiedActiveInsurancePolicy(UUID patientId, LocalDate onDate);

    /** Date of birth and gender — clinical/encounters need these for age-band vital ranges and pregnancy-prompt eligibility. */
    Optional<PatientDemographics> findDemographics(UUID patientId);

    /** Name and hospital number — 12-pharmacist.md §4.6's dispensing label needs these; nothing clinical. */
    Optional<PatientIdentity> findIdentity(UUID patientId);
}
