package com.adags.modules.inpatient.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 17-ward-nurse.md §5.1: "Ceftriaxone 1g IV, TDS, 5 days -> 15 slots."
// Pure domain test, no Spring context (backend/CLAUDE.md "Domain layer").
class MedicationScheduleGeneratorTest {

    @Test
    void tdsForFiveDaysGenerates15EvenlySpacedSlots() {
        Instant start = Instant.parse("2026-01-01T14:00:00Z");

        List<Instant> slots = MedicationScheduleGenerator.generate(MedicationFrequency.TDS, 5, start);

        assertThat(slots).hasSize(15);
        assertThat(slots.get(0)).isEqualTo(start);
        assertThat(Duration.between(slots.get(0), slots.get(1))).isEqualTo(Duration.ofHours(8));
        assertThat(slots).isSorted();
    }

    @Test
    void statGeneratesExactlyOneSlotAtStartTimeIgnoringDuration() {
        Instant start = Instant.parse("2026-01-01T14:37:00Z");

        List<Instant> slots = MedicationScheduleGenerator.generate(MedicationFrequency.STAT, 5, start);

        assertThat(slots).containsExactly(start);
    }

    @Test
    void prnGeneratesNoSlots() {
        List<Instant> slots = MedicationScheduleGenerator.generate(
                MedicationFrequency.PRN, 5, Instant.parse("2026-01-01T14:00:00Z"));

        assertThat(slots).isEmpty();
    }

    @Test
    void aRepeatingFrequencyRoundsTheFirstSlotUpToTheNextFullHour() {
        Instant start = Instant.parse("2026-01-01T14:37:00Z");

        List<Instant> slots = MedicationScheduleGenerator.generate(MedicationFrequency.OD, 1, start);

        assertThat(slots).containsExactly(Instant.parse("2026-01-01T15:00:00Z"));
    }

    @Test
    void aRepeatingFrequencyWithNoDurationIsRejected() {
        assertThatThrownBy(() -> MedicationScheduleGenerator.generate(
                MedicationFrequency.BD, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
