package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.application.DispenseLabel;

import java.time.LocalDate;
import java.util.List;

public record DispenseLabelResponse(
        String patientName,
        String hospitalNumber,
        String drugName,
        String doseInstructions,
        LocalDate dispensedDate,
        List<LabelBatchLineResponse> batchLines
) {

    public static DispenseLabelResponse from(DispenseLabel label) {
        return new DispenseLabelResponse(label.patientName(), label.hospitalNumber(), label.drugName(),
                label.doseInstructions(), label.dispensedDate(),
                label.batchLines().stream().map(LabelBatchLineResponse::from).toList());
    }
}
