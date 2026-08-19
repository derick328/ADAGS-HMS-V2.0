package com.adags.modules.pharmacy;

import com.adags.shared.kernel.DispensedBatchSummary;
import com.adags.shared.kernel.PrescriptionLineMarSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). Built for inpatient's MAR: 17-ward-nurse.md §5.1 needs drug/
// dose/route read live from the prescription (never duplicated onto
// MedicationAdministration), and §5.2 needs the batch a dose was already
// dispensed from (link, don't decrement). Never a
// com.adags.modules.pharmacy.domain type across this boundary.
public interface PharmacyFacade {

    /** 17-ward-nurse.md §5.1: the read-only drug/dose/route/frequency the MAR renders for one prescription line. */
    Optional<PrescriptionLineMarSummary> findPrescriptionLineForMar(UUID prescriptionLineId);

    /**
     * 17-ward-nurse.md §5.2: every batch already dispensed against this
     * line, for the nurse to pick from when administering (a line split
     * across batches has more than one entry). Empty if nothing has been
     * dispensed yet.
     */
    List<DispensedBatchSummary> findDispensedBatches(UUID prescriptionLineId);
}
