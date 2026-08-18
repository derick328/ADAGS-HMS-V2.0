package com.adags.modules.pharmacy.application;

import com.adags.modules.pharmacy.domain.Dispense;
import com.adags.modules.pharmacy.domain.DispenseLine;
import com.adags.modules.pharmacy.domain.PrescriptionLine;

import java.util.List;

public record DispenseResult(Dispense dispense, List<DispenseLine> lines, PrescriptionLine prescriptionLine) {
}
