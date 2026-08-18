package com.adags.modules.inventory.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemTest {

    @Test
    void createStartsUnclassifiedAndInactive() {
        Item item = Item.create("AMX500", "Amoxicillin 500mg capsule", "ANTIBIOTIC", "CAPSULE", "500mg", "CAPSULE",
                100, UUID.randomUUID());

        assertThat(item.regulatorySchedule()).isEqualTo(RegulatorySchedule.UNCLASSIFIED);
        assertThat(item.active()).isFalse();
    }

    @Test
    void rejectsANegativeReorderLevel() {
        assertThatThrownBy(() -> Item.create("AMX500", "Amoxicillin", "ANTIBIOTIC", "CAPSULE", "500mg", "CAPSULE",
                -1, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assertDispensableRejectsAControlledItem() {
        Item item = new Item(UUID.randomUUID(), "MOR10", "Morphine sulfate 10mg tablet", "OPIOID", "TABLET", "10mg",
                "TABLET", RegulatorySchedule.CONTROLLED, 0, false, UUID.randomUUID(), null);

        assertThatThrownBy(item::assertDispensable).isInstanceOf(ControlledItemBlockedException.class);
    }

    @Test
    void assertDispensableAllowsUnclassified() {
        Item item = Item.create("AMX500", "Amoxicillin 500mg capsule", "ANTIBIOTIC", "CAPSULE", "500mg", "CAPSULE",
                100, UUID.randomUUID());

        item.assertDispensable();
    }
}
