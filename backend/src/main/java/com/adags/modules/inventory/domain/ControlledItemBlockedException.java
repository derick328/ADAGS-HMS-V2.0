package com.adags.modules.inventory.domain;

import java.util.UUID;

// 14-controlled-medicines-tanzania.md §2/§3, 13-controlled-medicines-
// policy.md §3: CONTROLLED is blocked from catalogue activation, pricing,
// prescribing, dispensing, restock and transfer. Thrown by
// Item.assertDispensable(), called from every write path that could move
// a controlled item — the database triggers in
// V24__regulatory_schedule_enforcement.sql are the belt-and-braces backstop
// for a path that forgets to call it.
public class ControlledItemBlockedException extends RuntimeException {

    private final UUID itemId;

    public ControlledItemBlockedException(UUID itemId) {
        super("Item " + itemId + " is CONTROLLED and cannot be dispensed, activated, restocked, or prescribed.");
        this.itemId = itemId;
    }

    public UUID itemId() {
        return itemId;
    }
}
