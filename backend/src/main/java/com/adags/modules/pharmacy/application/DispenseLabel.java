package com.adags.modules.pharmacy.application;

import java.time.LocalDate;
import java.util.List;

// 12-pharmacist.md §4.6: "A patient handed unlabelled tablets is a dosing
// error waiting to happen, and the batch number on the label is what
// makes a recall traceable to an individual." This is the payload only —
// rendering (layout, printing) is the frontend's job. batchLines is one
// entry per distinct batch the dispense drew from — a 30-tablet dispense
// split across two batches carries two batch numbers, both real, neither
// hidden.
public record DispenseLabel(
        String patientName,
        String hospitalNumber,
        String drugName,
        String doseInstructions,
        LocalDate dispensedDate,
        List<LabelBatchLine> batchLines
) {
}
