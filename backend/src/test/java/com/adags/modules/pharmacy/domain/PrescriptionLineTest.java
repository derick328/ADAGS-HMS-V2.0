package com.adags.modules.pharmacy.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 12-pharmacist.md §4.4's three-way rule, as a pure domain test.
class PrescriptionLineTest {

    private PrescriptionLine fixture(int quantityPrescribed) {
        return PrescriptionLine.create(UUID.randomUUID(), UUID.randomUUID(), quantityPrescribed,
                "TAKE ONE TABLET THREE TIMES A DAY", UUID.randomUUID(), false);
    }

    @Test
    void partialDispenseLeavesTheLinePartiallyDispensed() {
        PrescriptionLine line = fixture(30).applyDispense(12);

        assertThat(line.status()).isEqualTo(PrescriptionLineStatus.PARTIALLY_DISPENSED);
        assertThat(line.quantityDispensed()).isEqualTo(12);
        assertThat(line.remaining()).isEqualTo(18);
    }

    @Test
    void dispensingExactlyTheRemainderCompletesTheLine() {
        PrescriptionLine line = fixture(30).applyDispense(30);

        assertThat(line.status()).isEqualTo(PrescriptionLineStatus.COMPLETED);
        assertThat(line.remaining()).isZero();
    }

    @Test
    void dispensingMoreThanRemainingIsAHardBlock() {
        PrescriptionLine line = fixture(30);

        assertThatThrownBy(() -> line.applyDispense(31)).isInstanceOf(DispenseExceedsPrescribedException.class);
    }

    @Test
    void twoPartialDispensesThatExactlySumToTheRemainderComplete() {
        PrescriptionLine afterFirst = fixture(30).applyDispense(12);
        PrescriptionLine afterSecond = afterFirst.applyDispense(18);

        assertThat(afterSecond.status()).isEqualTo(PrescriptionLineStatus.COMPLETED);
    }

    @Test
    void aSecondDispenseExceedingWhatRemainsIsAHardBlock() {
        PrescriptionLine afterFirst = fixture(30).applyDispense(12);

        assertThatThrownBy(() -> afterFirst.applyDispense(19)).isInstanceOf(DispenseExceedsPrescribedException.class);
    }

    @Test
    void createRejectsAZeroQuantity() {
        assertThatThrownBy(() -> fixture(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
