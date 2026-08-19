package com.adags.modules.inpatient.domain;

// Mirrors pharmacy.domain.PrescriptionFrequency's values deliberately, not
// imported from it — root backend/CLAUDE.md "Modules": no module imports
// another module's domain types. InpatientFacade's
// generateMedicationAdministrationSchedule crosses that boundary with the
// enum name as a raw String (same treatment ClinicalFacade gives Avpu),
// and PharmacyFacadeImpl/InpatientFacadeImpl each own their side's copy.
public enum MedicationFrequency {
    STAT(1),
    OD(1),
    BD(2),
    TDS(3),
    QDS(4),
    PRN(0);

    private final int timesPerDay;

    MedicationFrequency(int timesPerDay) {
        this.timesPerDay = timesPerDay;
    }

    public int timesPerDay() {
        return timesPerDay;
    }

    public boolean isSingleDose() {
        return this == STAT;
    }
}
