package com.adags.modules.pharmacy.domain;

import com.adags.shared.kernel.BatchAvailability;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 12-pharmacist.md §5.3: "The system proposes batches in first-expiry-
// first-out order and fills the quantity automatically."
class FefoAllocatorTest {

    @Test
    void fillsEntirelyFromTheEarliestExpiringBatchWhenItHasEnough() {
        UUID itemId = UUID.randomUUID();
        UUID batchA = UUID.randomUUID();
        UUID batchB = UUID.randomUUID();
        List<BatchAvailability> batches = List.of(
                new BatchAvailability(batchA, itemId, "A", LocalDate.now().plusMonths(1), 50),
                new BatchAvailability(batchB, itemId, "B", LocalDate.now().plusMonths(6), 50));

        List<BatchAllocation> result = FefoAllocator.allocate(itemId, batches, 30);

        assertThat(result).containsExactly(new BatchAllocation(batchA, 30));
    }

    @Test
    void spillsIntoTheNextBatchWhenTheFirstIsInsufficient() {
        UUID itemId = UUID.randomUUID();
        UUID batchA = UUID.randomUUID();
        UUID batchB = UUID.randomUUID();
        List<BatchAvailability> batches = List.of(
                new BatchAvailability(batchA, itemId, "A", LocalDate.now().plusMonths(1), 20),
                new BatchAvailability(batchB, itemId, "B", LocalDate.now().plusMonths(6), 30));

        List<BatchAllocation> result = FefoAllocator.allocate(itemId, batches, 30);

        assertThat(result).containsExactly(new BatchAllocation(batchA, 20), new BatchAllocation(batchB, 10));
    }

    @Test
    void rejectsAQuantityExceedingTotalAvailability() {
        UUID itemId = UUID.randomUUID();
        List<BatchAvailability> batches = List.of(
                new BatchAvailability(UUID.randomUUID(), itemId, "A", LocalDate.now().plusMonths(1), 10));

        assertThatThrownBy(() -> FefoAllocator.allocate(itemId, batches, 11))
                .isInstanceOf(InsufficientBatchStockException.class);
    }

    @Test
    void rejectsAZeroQuantity() {
        assertThatThrownBy(() -> FefoAllocator.allocate(UUID.randomUUID(), List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
