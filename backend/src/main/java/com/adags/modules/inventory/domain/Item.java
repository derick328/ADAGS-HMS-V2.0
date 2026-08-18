package com.adags.modules.inventory.domain;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §9.1/§12: identity, name, classification, form, strength
// and reorder_level only — cost lives on StockBatch, selling price on
// PriceListEntry (billing module), never a price field here (§9.1: "Stored
// on the item, per-payer pricing is impossible and every insured patient's
// invoice is wrong").
public record Item(
        UUID id,
        String code,
        String name,
        String classification,
        String form,
        String strength,
        String unit,
        RegulatorySchedule regulatorySchedule,
        int reorderLevel,
        boolean active,
        UUID createdBy,
        Instant createdAt
) {

    // Starts UNCLASSIFIED (the legal default, 14-*.md §5) and inactive —
    // classification and activation are separate, later actions.
    public static Item create(String code, String name, String classification, String form, String strength,
                               String unit, int reorderLevel, UUID createdBy) {
        if (reorderLevel < 0) {
            throw new IllegalArgumentException("reorderLevel must be >= 0.");
        }
        return new Item(null, code, name, classification, form, strength, unit,
                RegulatorySchedule.UNCLASSIFIED, reorderLevel, false, createdBy, null);
    }

    // 13-controlled-medicines-policy.md §3.1's PharmacyRepository.assertDispensable
    // pseudocode, as a pure domain check so every write path (activation,
    // restock, prescribing, dispensing) can call it before persisting —
    // "so a developer adding an endpoint next quarter inherits the block
    // without knowing it exists." Database triggers are the backstop for
    // a path that skips this call; this is the friendly-error half.
    public void assertDispensable() {
        if (!regulatorySchedule.isDispensable()) {
            throw new ControlledItemBlockedException(id);
        }
    }
}
