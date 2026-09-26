package com.adags.modules.surgery.api;

import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedurePhase;

import java.time.Instant;
import java.util.UUID;

// preparedByName / reconciledByName / attestedByName are resolved by
// the controller via IdentityFacade (root CLAUDE.md "Modules": no
// module holds a user's display name — surgery must not import from
// identity.domain). Nullable when the id doesn't resolve to a live user
// row or when the corresponding step hasn't happened yet — the caller
// falls back to "—" or the raw id, never to a fabricated label.
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
        String preparedByName,
        Instant preparedAt,
        UUID reconciledBy,
        String reconciledByName,
        UUID attestedBy,
        String attestedByName,
        Instant reconciledAt,
        boolean deferred
) {
    public static ProcedureItemResponse from(ProcedureItem item) {
        return from(item, null, null, null);
    }

    public static ProcedureItemResponse from(
            ProcedureItem item, String preparedByName, String reconciledByName, String attestedByName
    ) {
        return new ProcedureItemResponse(item.id(), item.surgeryOrderId(), item.itemId(), item.phase(),
                item.quantityPrepared(), item.quantityUsed(), item.quantityReturned(), item.quantityWasted(),
                item.wastageReason(), item.batchId(),
                item.preparedBy(), preparedByName, item.preparedAt(),
                item.reconciledBy(), reconciledByName,
                item.attestedBy(), attestedByName,
                item.reconciledAt(), item.isDeferred());
    }
}
