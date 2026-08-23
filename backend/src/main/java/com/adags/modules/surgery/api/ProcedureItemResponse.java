package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedurePhase;

import java.time.Instant;
import java.util.UUID;

public record ProcedureItemResponse(
        UUID id,
        UUID surgeryOrderId,
        UUID itemId,
        ProcedurePhase phase,
        int quantityPrepared,
        int quantityUsed,
        int quantityReturned,
        int quantityWasted,
        String wastageReason,
        UUID batchId,
        UUID preparedBy,
        Instant preparedAt,
        UUID reconciledBy,
        UUID attestedBy,
        Instant reconciledAt,
        boolean deferred
) {
    public static ProcedureItemResponse from(ProcedureItem item) {
        return new ProcedureItemResponse(item.id(), item.surgeryOrderId(), item.itemId(), item.phase(),
                item.quantityPrepared(), item.quantityUsed(), item.quantityReturned(), item.quantityWasted(),
                item.wastageReason(), item.batchId(), item.preparedBy(), item.preparedAt(), item.reconciledBy(),
                item.attestedBy(), item.reconciledAt(), item.isDeferred());
    }
}
