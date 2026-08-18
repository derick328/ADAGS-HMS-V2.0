package com.adags.modules.pharmacy.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record DispenseRequest(
        @NotNull UUID prescriptionLineId,
        @NotEmpty @Valid List<DispenseLineRequest> lines,
        @NotBlank String idempotencyKey,
        String counsellingNotes,
        String dispensingNotes
) {
}
