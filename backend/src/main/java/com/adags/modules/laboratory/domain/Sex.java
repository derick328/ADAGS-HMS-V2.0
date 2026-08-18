package com.adags.modules.laboratory.domain;

// Biological sex for reference-range applicability, not patient.gender's
// identity vocabulary — same distinction clinical.domain.Sex draws for
// vital_reference_range. Each module keeps its own copy rather than
// sharing clinical's (root CLAUDE.md "Modules": no cross-module imports).
public enum Sex {
    MALE,
    FEMALE,
    ANY;

    // OTHER and PREFER_NOT_TO_SAY have no sex-specific physiological range
    // to select — ANY is the only defensible mapping, not a guess (same
    // reasoning as clinical.domain.Sex.fromPatientGender).
    public static Sex fromPatientGender(String patientGender) {
        return switch (patientGender) {
            case "MALE" -> MALE;
            case "FEMALE" -> FEMALE;
            default -> ANY;
        };
    }
}
