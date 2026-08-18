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

    /** Date of birth and gender — clinical/encounters need these for age-band vital ranges and pregnancy-prompt eligibility. */
    Optional<PatientDemographics> findDemographics(UUID patientId);

    /** Name and hospital number — 12-pharmacist.md §4.6's dispensing label needs these; nothing clinical. */
    Optional<PatientIdentity> findIdentity(UUID patientId);
}
