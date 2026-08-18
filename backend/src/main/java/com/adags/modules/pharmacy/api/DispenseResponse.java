package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.application.DispenseResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DispenseResponse(
        UUID id,
        UUID prescriptionLineId,
        Instant dispensedAt,
        int quantityDispensed,
        List<DispenseLineResponse> lines,
        PrescriptionLineResponse prescriptionLine
) {

    public static DispenseResponse from(DispenseResult result) {
        return new DispenseResponse(result.dispense().id(), result.dispense().prescriptionLineId(),
                result.dispense().dispensedAt(), result.dispense().quantityDispensed(),
                result.lines().stream().map(DispenseLineResponse::from).toList(),
                PrescriptionLineResponse.from(result.prescriptionLine()));
    }
}
