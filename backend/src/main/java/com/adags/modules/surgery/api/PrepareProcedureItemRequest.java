package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.ProcedurePhase;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

// batchId is nullable: the ward-nurse UI shouldn't ask a circulating
// nurse to type a stock-batch UUID (13-controlled-medicines-policy.md /
// 12-pharmacist.md §5.3 already established that the server picks the
// FEFO batch for pharmacy dispenses; procedure-item preparation is the
// same physical-pull, so the same rule applies). When null, the service
// resolves the earliest-expiring ACTIVE batch for this item; a caller
// that already knows which batch was physically pulled (a future
// scanner integration) can still pass it explicitly.
public record PrepareProcedureItemRequest(
        @NotNull UUID surgeryOrderId,
        @NotNull UUID itemId,
        @NotNull ProcedurePhase phase,
        @Positive int quantityPrepared,
        UUID batchId
) {
}
