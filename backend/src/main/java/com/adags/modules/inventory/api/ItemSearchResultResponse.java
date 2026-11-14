package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import com.adags.shared.kernel.DosageForm;

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
        String unit,
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
        } else if (DosageForm.fromCatalogue(item.form()) == null) {
            reason = "Dosage form is not configured. Submit a catalogue correction before prescribing.";
        } else if (DosageForm.fromCatalogue(item.form()).automaticQuantity()
                && !DosageForm.fromCatalogue(item.form()).baseUnit().equalsIgnoreCase(item.unit())) {
            reason = "Dosage form and base inventory unit differ. Correct the catalogue presentation before prescribing.";
        }
        selectable = selectable && reason == null;
        return new ItemSearchResultResponse(item.id(), item.code(), item.name(), item.form(), item.strength(), item.unit(),
                item.regulatorySchedule(), selectable, reason);
    }

    /**
     * V67 admin importer visibility: the row exists in the shared
     * catalogue but no inventory.item classifies it yet. Same
     * "visible-and-unselectable" pattern as CONTROLLED — the doctor
     * sees it and knows why they cannot prescribe it. The id is the
     * service_item UUID so the client can key by it; it will not
     * resolve to an Item at prescribe time, so a bypass in the UI
     * still fails cleanly at the backend.
     */
    public static ItemSearchResultResponse pendingClassification(
            java.util.UUID serviceItemId, String code, String name) {
        return new ItemSearchResultResponse(
                serviceItemId, code, name, "—", null, null,
                RegulatorySchedule.UNCLASSIFIED, false,
                "Pending pharmacy classification — a pharmacist must set form, unit and regulatory schedule before this can be prescribed.");
    }
}
