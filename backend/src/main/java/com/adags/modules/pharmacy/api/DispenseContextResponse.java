package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.application.DispenseContext;

import java.util.List;

public record DispenseContextResponse(
        PrescriptionLineResponse prescriptionLine,
        ItemSummaryResponse item,
        List<AllergyResponse> allergies,
        List<BatchAvailabilityResponse> availableBatches,
        List<BatchAllocationResponse> fefoSuggestion,
        Double weightKg
) {

    public static DispenseContextResponse from(DispenseContext context) {
        return new DispenseContextResponse(
                PrescriptionLineResponse.from(context.prescriptionLine()),
                ItemSummaryResponse.from(context.item()),
                context.allergies().stream().map(AllergyResponse::from).toList(),
                context.availableBatches().stream().map(BatchAvailabilityResponse::from).toList(),
                context.fefoSuggestion().stream().map(BatchAllocationResponse::from).toList(),
                context.weightKg());
    }
}
