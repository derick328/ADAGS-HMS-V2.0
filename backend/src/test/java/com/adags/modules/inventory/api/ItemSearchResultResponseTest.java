package com.adags.modules.inventory.api;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.RegulatorySchedule;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 13-controlled-medicines-policy.md §4: "The item is visible and
// unselectable, with the reason stated" — never silently absent.
class ItemSearchResultResponseTest {

    @Test
    void aControlledItemIsVisibleButUnselectableWithAReason() {
        Item item = new Item(UUID.randomUUID(), "MOR10", "Morphine sulfate 10mg tablet", "OPIOID", "TABLET", "10mg",
                "TABLET", RegulatorySchedule.CONTROLLED, 0, false, UUID.randomUUID(), null);

        ItemSearchResultResponse response = ItemSearchResultResponse.from(item);

        assertThat(response.selectable()).isFalse();
        assertThat(response.unselectableReason()).contains("Controlled medicine");
    }

    @Test
    void anActiveDispensableItemIsSelectable() {
        Item item = new Item(UUID.randomUUID(), "AMX500", "Amoxicillin 500mg capsule", "ANTIBIOTIC", "CAPSULE",
                "500mg", "CAPSULE", RegulatorySchedule.PHARMACY_ONLY_OTC, 100, true, UUID.randomUUID(), null);

        ItemSearchResultResponse response = ItemSearchResultResponse.from(item);

        assertThat(response.selectable()).isTrue();
        assertThat(response.unselectableReason()).isNull();
    }

    @Test
    void anInactiveNonControlledItemIsUnselectableWithADifferentReason() {
        Item item = new Item(UUID.randomUUID(), "AMX500", "Amoxicillin 500mg capsule", "ANTIBIOTIC", "CAPSULE",
                "500mg", "CAPSULE", RegulatorySchedule.PHARMACY_ONLY_OTC, 100, false, UUID.randomUUID(), null);

        ItemSearchResultResponse response = ItemSearchResultResponse.from(item);

        assertThat(response.selectable()).isFalse();
        assertThat(response.unselectableReason()).doesNotContain("Controlled medicine");
    }
}
