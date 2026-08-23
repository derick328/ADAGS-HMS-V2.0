package com.adags.modules.billing.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateDraftInvoiceCommand(
        UUID patientId,
        /** Nullable — a pharmacy walk-in or records fee has no encounter (02-billing-architecture.md §11). */
        UUID encounterId,
        UUID facilityId,
        LocalDate dueDate,
        String notes,
        List<Line> lines,
        UUID actorUserId
) {

    public record Line(String serviceItemCode, int quantity) {
    }
}
