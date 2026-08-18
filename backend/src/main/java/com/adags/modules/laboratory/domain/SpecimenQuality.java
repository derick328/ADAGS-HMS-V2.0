package com.adags.modules.laboratory.domain;

// 15-lab-technician.md §4.2.
public enum SpecimenQuality {
    ADEQUATE,
    HAEMOLYSED,
    CLOTTED,
    INSUFFICIENT_VOLUME,
    WRONG_CONTAINER,
    MISLABELLED,
    DELAYED_TRANSPORT;

    public boolean isPatientSafetyIncident() {
        // §4.3: "MISLABELLED deserves separate treatment: it is a patient-
        // safety incident, not just a quality issue. Route it to
        // CLINICAL_LEAD as well as the ordering doctor."
        return this == MISLABELLED;
    }
}
