package com.adags.modules.clinical.domain;

// Biological sex for VitalReferenceRange lookup — distinct from
// patient.gender's identity enum (MALE/FEMALE/OTHER/PREFER_NOT_TO_SAY).
// 06-triage.md §2.2 calls the range table "sex-aware" without giving
// sex-specific numbers; ANY is the only value seeded today.
public enum Sex {
    MALE,
    FEMALE,
    ANY;

    // OTHER and PREFER_NOT_TO_SAY have no sex-specific physiological range
    // to select — ANY is the only defensible mapping, not a guess.
    public static Sex fromPatientGender(String patientGender) {
        return switch (patientGender) {
            case "MALE" -> MALE;
            case "FEMALE" -> FEMALE;
            default -> ANY;
        };
    }
}
