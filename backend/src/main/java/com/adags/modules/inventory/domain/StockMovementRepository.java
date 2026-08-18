package com.adags.modules.inventory.domain;

public interface StockMovementRepository {

    // Append-only — no update/delete method exists on this interface by
    // design (root CLAUDE.md "Data").
    StockMovement insert(StockMovement movement);
}
