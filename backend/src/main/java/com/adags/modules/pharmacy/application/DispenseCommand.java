package com.adags.modules.pharmacy.application;

import java.util.List;
import java.util.UUID;

public record DispenseCommand(
        UUID prescriptionLineId,
        List<DispenseLineInput> lines,
        UUID actorUserId,
        String idempotencyKey,
        String counsellingNotes,
        String dispensingNotes
) {
}
