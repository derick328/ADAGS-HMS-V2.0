package com.adags.modules.inpatient.domain;

import java.util.UUID;

// 17-ward-nurse.md §5.3: Item.requires_administration_witness medicines
// need a second person, distinct from whoever administered — enforced here
// so the CHECK (witness_user_id != administered_by) at the DB is a backstop,
// not the only guard.
public class MedicationWitnessRequiredException extends RuntimeException {

    public MedicationWitnessRequiredException(UUID prescriptionLineId) {
        super("This medicine requires a witness distinct from the administering nurse: " + prescriptionLineId);
    }
}
