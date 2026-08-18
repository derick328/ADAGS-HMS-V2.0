package com.adags.modules.inpatient.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 04-procedure-bedday-rules.md §3 — the 10:00 rule. §3.2's worked table,
// verbatim, plus the two properties §3.3 calls out explicitly.
class BedDayCalculatorTest {

    private static final ZoneId FACILITY_ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    // §3.2's table. Mon/Tue/Wed are arbitrary consecutive calendar days —
    // the algorithm has no day-of-week dependency, only the 10:00 clock
    // boundary, so any three consecutive dates prove the same thing.
    @ParameterizedTest(name = "{0} -> {1} bills {2} day(s)")
    @CsvSource({
            "2026-03-02T23:00, 2026-03-03T08:00, 1", // Mon 23:00 -> Tue 08:00, 9h
            "2026-03-02T23:00, 2026-03-03T11:00, 2", // Mon 23:00 -> Tue 11:00, 12h
            "2026-03-02T06:01, 2026-03-03T10:00, 1", // Mon 06:01 -> Tue 10:00, 28h
            "2026-03-02T06:00, 2026-03-02T11:00, 1", // Mon 06:00 -> Mon 11:00, 5h
            "2026-03-02T11:00, 2026-03-02T14:00, 0", // Mon 11:00 -> Mon 14:00, 3h (free)
            "2026-03-02T14:00, 2026-03-04T09:00, 2", // Mon 14:00 -> Wed 09:00, 43h
            "2026-03-02T06:30, 2026-03-02T09:30, 0", // Mon 06:30 -> Mon 09:30, 3h (free)
    })
    void workedExamplesFromTheSpec(String admission, String billingStop, int expectedDays) {
        Instant admissionTime = local(admission);
        Instant stop = local(billingStop);

        assertThat(BedDayCalculator.daysFor(admissionTime, stop, FACILITY_ZONE)).isEqualTo(expectedDays);
    }

    // §3.3: "The grace is measured from actual admission, not effective
    // start." Both stays below bump to the same 10:00 effective start —
    // Mon 06:00 -> Mon 11:00 is only 1h past that effective start yet
    // bills (5h actual, over the 4h floor); Mon 07:00 -> Mon 09:30 is 1h
    // *before* its own effective start yet stays free (2h30 actual, under
    // the 4h floor). Effective start alone cannot explain either result —
    // only the actual-admission-time check does.
    @Test
    void theFourHourGraceIsMeasuredFromActualAdmissionNotEffectiveStart() {
        Instant fiveHoursActual = local("2026-03-02T06:00");
        assertThat(BedDayCalculator.daysFor(fiveHoursActual, local("2026-03-02T11:00"), FACILITY_ZONE)).isEqualTo(1);

        Instant underFourHoursActual = local("2026-03-02T07:00");
        assertThat(BedDayCalculator.daysFor(underFourHoursActual, local("2026-03-02T09:30"), FACILITY_ZONE)).isZero();
    }

    // §3.3: "A 05:59 admission counts from admission while 06:00 counts
    // from 10:00." The sharp boundary is intentional, not a rounding artifact.
    @Test
    void effectiveStartIsSharpAtTheMorningWindowBoundary() {
        Instant justBeforeWindow = local("2026-03-02T05:59");
        Instant atWindowStart = local("2026-03-02T06:00");
        Instant justBeforeWindowEnd = local("2026-03-02T09:59");
        Instant atWindowEnd = local("2026-03-02T10:00");

        assertThat(BedDayCalculator.effectiveStart(justBeforeWindow, FACILITY_ZONE)).isEqualTo(justBeforeWindow);
        assertThat(BedDayCalculator.effectiveStart(atWindowStart, FACILITY_ZONE)).isEqualTo(local("2026-03-02T10:00"));
        assertThat(BedDayCalculator.effectiveStart(justBeforeWindowEnd, FACILITY_ZONE)).isEqualTo(local("2026-03-02T10:00"));
        assertThat(BedDayCalculator.effectiveStart(atWindowEnd, FACILITY_ZONE)).isEqualTo(atWindowEnd);
    }

    // §3.1: "A discharge at exactly 10:00 has not passed it" — the
    // boundary is strictly before billing_stop, not <=.
    @Test
    void aBoundaryAtExactlyBillingStopHasNotBeenPassed() {
        Instant effectiveStart = local("2026-03-02T10:00");
        Instant stopExactlyOnTheNextBoundary = local("2026-03-03T10:00");

        assertThat(BedDayCalculator.boundariesPassed(effectiveStart, stopExactlyOnTheNextBoundary, FACILITY_ZONE)).isZero();
    }

    // The job's catch-up sweep is inclusive of "now" landing exactly on a
    // boundary (the job runs AT 10:00) — the opposite convention from
    // boundariesBetween's discharge-time semantics, deliberately.
    @Test
    void boundariesToPostIncludesABoundaryLandingExactlyOnNow() {
        Instant lastPosted = local("2026-03-02T10:00");
        Instant now = local("2026-03-03T10:00");

        assertThat(BedDayCalculator.boundariesToPost(lastPosted, now, FACILITY_ZONE))
                .containsExactly(local("2026-03-03T10:00"));
    }

    @Test
    void boundariesToPostIsEmptyWhenNoFullNightHasElapsedSinceTheLastPost() {
        Instant lastPosted = local("2026-03-02T10:00");
        Instant now = local("2026-03-03T09:59");

        assertThat(BedDayCalculator.boundariesToPost(lastPosted, now, FACILITY_ZONE)).isEmpty();
    }

    private static Instant local(String isoLocalDateTime) {
        return ZonedDateTime.of(LocalDateTime.parse(isoLocalDateTime), FACILITY_ZONE).toInstant();
    }
}
