package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.ProcedurePhase;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record PrepareProcedureItemRequest(
        @NotNull UUID surgeryOrderId,
        @NotNull UUID itemId,
        @NotNull ProcedurePhase phase,
        @Positive int quantityPrepared,
        @NotNull UUID batchId
) {
}
