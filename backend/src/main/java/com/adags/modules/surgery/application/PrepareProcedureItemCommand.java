package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.ProcedurePhase;

import java.util.UUID;

public record PrepareProcedureItemCommand(
        UUID surgeryOrderId,
        UUID itemId,
        ProcedurePhase phase,
        int quantityPrepared,
        UUID batchId,
        UUID preparedBy
) {
}
