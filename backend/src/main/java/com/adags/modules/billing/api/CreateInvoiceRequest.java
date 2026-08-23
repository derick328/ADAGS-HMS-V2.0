package com.adags.modules.billing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotNull UUID patientId,
        /** Nullable — defaults to the patient's open encounter on the frontend; a standalone invoice omits it. */
        UUID encounterId,
        @NotNull UUID facilityId,
        @NotNull LocalDate dueDate,
        String notes,
        @NotEmpty List<@Valid CreateInvoiceLineRequest> lines
) {
}
