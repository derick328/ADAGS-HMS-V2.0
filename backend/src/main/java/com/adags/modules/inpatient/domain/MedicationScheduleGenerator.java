package com.adags.modules.inpatient.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// 17-ward-nurse.md §5.1: "Doctor prescribes Ceftriaxone 1g IV, TDS, 5 days
// -> system generates 15 slots." Pure, no I/O, no Spring — unit-testable on
// its own (root backend/CLAUDE.md "Domain layer has no Spring annotations
// and no I/O").
//
// Slots are spaced evenly across 24h from the first slot, which is
// startAt rounded up to the next full hour — an interim, documented
// calibration (same honesty as backend/CLAUDE.md's duplicate-detection
// threshold comment), not a clinical dosing-schedule standard. timesPerDay
// x durationDays total slots for a repeating frequency; exactly one slot,
// at startAt itself (no rounding — "STAT" means now), for a single dose.
// PRN (timesPerDay() == 0) generates nothing — administrations against a
// PRN line are recorded ad hoc, out of scope for this mechanism.
public final class MedicationScheduleGenerator {

    private MedicationScheduleGenerator() {
    }

    public static List<Instant> generate(MedicationFrequency frequency, int durationDays, Instant startAt) {
        if (frequency == null) {
            throw new IllegalArgumentException("frequency is required.");
        }
        if (frequency.isSingleDose()) {
            return List.of(startAt);
        }
        int timesPerDay = frequency.timesPerDay();
        if (timesPerDay <= 0) {
            return List.of();
        }
        if (durationDays <= 0) {
            throw new IllegalArgumentException("durationDays must be > 0 for a repeating frequency.");
        }

        Instant firstSlot = startAt.truncatedTo(ChronoUnit.HOURS);
        if (firstSlot.isBefore(startAt)) {
            firstSlot = firstSlot.plus(1, ChronoUnit.HOURS);
        }

        long intervalHours = 24L / timesPerDay;
        int totalSlots = timesPerDay * durationDays;

        List<Instant> slots = new ArrayList<>(totalSlots);
        for (int i = 0; i < totalSlots; i++) {
            slots.add(firstSlot.plus(Duration.ofHours(intervalHours * i)));
        }
        return slots;
    }
}
