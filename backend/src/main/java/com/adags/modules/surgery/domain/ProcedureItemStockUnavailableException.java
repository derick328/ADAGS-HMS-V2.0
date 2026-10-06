package com.adags.modules.surgery.domain;

import java.util.UUID;

// Preparing a procedure item physically pulls it from theatre stock at
// prepare time (04-procedure-bedday-rules.md §2, PrepareProcedureItemService)
// — so it can only be prepared when a live, non-expired batch actually holds
// enough of it. When the circulating nurse doesn't type a batch, the service
// resolves the FEFO batch automatically; if no such batch exists this is the
// rejection.
//
// It is deliberately NOT a bare NoSuchElementException: that maps to a
// generic 404 "Resource not found." in GlobalExceptionHandler, which tells
// the operator nothing — they see "Resource not found." on a screen with no
// obviously-missing resource and cannot tell it is a stock problem. This
// carries a truthful, actionable message the UI shows verbatim.
public class ProcedureItemStockUnavailableException extends RuntimeException {

    private final UUID itemId;
    private final int quantityRequested;

    public ProcedureItemStockUnavailableException(UUID itemId, int quantityRequested) {
        super("No available stock for this item — ask the pharmacist to restock before preparing it "
                + "(needed " + quantityRequested + ", item " + itemId + ").");
        this.itemId = itemId;
        this.quantityRequested = quantityRequested;
    }

    public UUID itemId() {
        return itemId;
    }

    public int quantityRequested() {
        return quantityRequested;
    }
}
