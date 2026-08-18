package com.adags.modules.pharmacy.domain;

import java.util.UUID;

// 14-controlled-medicines-tanzania.md §3: "POST /prescriptions Rejected
// server-side even when a valid item_id is submitted directly." Pharmacy's
// own exception, not inventory's ControlledItemBlockedException — a
// module may not import another module's domain types (backend CLAUDE.md
// "Module boundary rules"), only call InventoryFacade.isDispensable(itemId)
// and react to the boolean.
public class ItemNotDispensableException extends RuntimeException {

    private final UUID itemId;

    public ItemNotDispensableException(UUID itemId) {
        super("Item " + itemId + " is not dispensable — controlled, inactive, or not found.");
        this.itemId = itemId;
    }

    public UUID itemId() {
        return itemId;
    }
}
