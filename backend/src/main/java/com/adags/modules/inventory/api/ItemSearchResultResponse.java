package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.RegulatorySchedule;

import java.util.UUID;

// 13-controlled-medicines-policy.md §4: a CONTROLLED item is visible and
// unselectable, with the reason stated — never silently absent from
// search results. selectable is false whenever the item cannot actually be
// prescribed/dispensed today (CONTROLLED, or not yet active).
public record ItemSearchResultResponse(
        UUID id,
        String code,
        String name,
        String form,
        String strength,
        RegulatorySchedule regulatorySchedule,
        boolean selectable,
        String unselectableReason
) {

    public static ItemSearchResultResponse from(Item item) {
        String reason = null;
        boolean selectable = item.active() && item.regulatorySchedule().isDispensable();
        if (!item.regulatorySchedule().isDispensable()) {
            reason = "Controlled medicine — prescribe on paper. Not available in this version.";
        } else if (!item.active()) {
            reason = "Not yet active in the catalogue.";
        }
        return new ItemSearchResultResponse(item.id(), item.code(), item.name(), item.form(), item.strength(),
                item.regulatorySchedule(), selectable, reason);
    }
}
