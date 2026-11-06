package com.adags.modules.pharmacy.application;

import com.adags.modules.pharmacy.domain.BatchAllocation;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionUnitSnapshot;
import com.adags.shared.kernel.AllergySummary;
import com.adags.shared.kernel.BatchAvailability;
import com.adags.shared.kernel.ItemSummary;

import java.util.List;

// 12-pharmacist.md §5.2: what the dispensing screen displays before the
// pharmacist confirms — allergies inline, the prescribed dose/route/
// frequency/duration, and the FEFO-suggested batch split for the
// remaining quantity.
public record DispenseContext(
        PrescriptionLine prescriptionLine,
        ItemSummary item,
        List<AllergySummary> allergies,
        List<BatchAvailability> availableBatches,
        List<BatchAllocation> fefoSuggestion,
        Double weightKg,
        PrescriptionUnitSnapshot unitSnapshot
) {
}
