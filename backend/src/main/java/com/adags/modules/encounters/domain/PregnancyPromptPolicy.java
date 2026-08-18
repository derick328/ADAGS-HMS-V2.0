package com.adags.modules.encounters.domain;

import java.time.LocalDate;
import java.time.Period;

// 06-triage.md §2.7: "Auto-appears when gender = FEMALE and age is 12-50...
// Age bounds are configuration, not constants." Pure function — the caller
// resolves the current bounds (a ProtocolSettingReader lookup, see
// V11__triage_assessment.sql's seeded keys) and the patient's gender/DOB (a
// PatientFacade lookup) and passes them in here, the same separation item 1
// drew between VitalReferenceRangeEvaluator and its repository-backed caller.
public final class PregnancyPromptPolicy {

    private PregnancyPromptPolicy() {
    }

    public static boolean isApplicable(String gender, LocalDate dateOfBirth, LocalDate asOf,
                                        int minAgeYears, int maxAgeYears) {
        if (!"FEMALE".equals(gender)) {
            return false;
        }
        int ageYears = Period.between(dateOfBirth, asOf).getYears();
        return ageYears >= minAgeYears && ageYears <= maxAgeYears;
    }
}
