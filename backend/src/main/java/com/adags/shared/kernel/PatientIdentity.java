package com.adags.shared.kernel;

// What PatientFacade hands another module (pharmacy, for the dispensing
// label — 12-pharmacist.md §4.6) needing the patient's name and
// hospital number. Never Patient itself (patients' own domain type)
// across the module boundary.
public record PatientIdentity(String hospitalNumber, String givenName, String middleName, String familyName) {

    public String fullName() {
        return middleName == null || middleName.isBlank()
                ? givenName + " " + familyName
                : givenName + " " + middleName + " " + familyName;
    }
}
