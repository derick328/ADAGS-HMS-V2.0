package com.adags.modules.inventory.domain;

import com.adags.shared.kernel.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockBatchTest {

    @Test
    void receiveRejectsABlankBatchNumber() {
        assertThatThrownBy(() -> StockBatch.receive(UUID.randomUUID(), UUID.randomUUID(), "  ",
                LocalDate.now().plusYears(1), new Money(1000, "TZS"), UUID.randomUUID(), "DN-1",
                UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void receiveRejectsABlankDeliveryNoteRef() {
        assertThatThrownBy(() -> StockBatch.receive(UUID.randomUUID(), UUID.randomUUID(), "BATCH-1",
                LocalDate.now().plusYears(1), new Money(1000, "TZS"), UUID.randomUUID(), " ",
                UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isExpiredAsOfIsTrueOnAndAfterTheExpiryDate() {
        StockBatch batch = StockBatch.receive(UUID.randomUUID(), UUID.randomUUID(), "BATCH-1",
                LocalDate.of(2026, 3, 1), new Money(1000, "TZS"), UUID.randomUUID(), "DN-1", UUID.randomUUID(), null);

        assertThat(batch.isExpiredAsOf(LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(batch.isExpiredAsOf(LocalDate.of(2026, 3, 1))).isTrue();
        assertThat(batch.isExpiredAsOf(LocalDate.of(2026, 3, 2))).isTrue();
    }
}
