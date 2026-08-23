package com.adags.modules.surgery.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureItemRepository {

    ProcedureItem insert(ProcedureItem procedureItem);

    Optional<ProcedureItem> findById(UUID id);

    List<ProcedureItem> findBySurgeryOrderId(UUID surgeryOrderId);

    /**
     * Writes the reconciliation counts and reconciledBy/reconciledAt.
     * WHERE-guarded on {@code reconciled_by IS NULL} — the real guard
     * against double-reconciling (and therefore double-crediting a
     * return), same idiom as every other WHERE-guarded status UPDATE in
     * this codebase. Returns false if the row was already reconciled.
     */
    boolean reconcile(UUID procedureItemId, int quantityUsed, int quantityReturned, int quantityWasted,
                       String wastageReason, UUID reconciledBy);

    /**
     * Writes attestedBy. WHERE-guarded on {@code reconciled_by IS NOT NULL
     * AND attested_by IS NULL}. Returns false if there is nothing to
     * attest yet, or it is already attested.
     */
    boolean attest(UUID procedureItemId, UUID attestedBy);
}
