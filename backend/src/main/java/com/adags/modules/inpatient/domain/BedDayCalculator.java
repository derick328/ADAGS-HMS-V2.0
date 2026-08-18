package com.adags.modules.inpatient.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

// 04-procedure-bedday-rules.md §3.1/§3.5 — the 10:00 rule, exactly:
//
//   if total_stay < 4 hours: days = 0
//   else:
//       effective_start = admission_time
//       if 06:00 <= admission_time <= 09:59: effective_start = 10:00 same day
//       days = boundaries_passed(effective_start, billing_stop) + 1
//
// Boundaries are 10:00 facility-local timestamps strictly AFTER
// effective_start and strictly BEFORE billing_stop — a discharge at
// exactly 10:00 has not passed that boundary. Pure, no I/O: the caller
// (WardChargeAccrualService for the ongoing job, and later the discharge
// service) supplies admission/billing-stop instants and the facility zone.
public final class BedDayCalculator {

    private static final LocalTime MORNING_WINDOW_START = LocalTime.of(6, 0);
    private static final LocalTime MORNING_WINDOW_END = LocalTime.of(10, 0);
    private static final Duration DEPARTURE_GRACE = Duration.ofHours(4);

    private BedDayCalculator() {
    }

    /**
     * §3.7: "gap = actual_departure - discharge_ordered_at. gap &lt;= 4h ->
     * billing_stop = discharge_ordered_at. gap &gt; 4h -> billing_stop =
     * actual_departure." Short delays the hospital caused (pharmacy
     * queue, transport, paperwork) cost the patient nothing; genuine
     * bed-blocking is billed. The result feeds daysFor as billingStop.
     */
    public static Instant billingStop(Instant dischargeOrderedAt, Instant actualDepartureAt) {
        Duration gap = Duration.between(dischargeOrderedAt, actualDepartureAt);
        return gap.compareTo(DEPARTURE_GRACE) <= 0 ? dischargeOrderedAt : actualDepartureAt;
    }

    /** §3.1's full formula: 0 if the stay is under 4 hours, else boundaries_passed + 1. */
    public static int daysFor(Instant admissionTime, Instant billingStop, ZoneId facilityZone) {
        if (Duration.between(admissionTime, billingStop).compareTo(Duration.ofHours(4)) < 0) {
            return 0;
        }
        Instant effectiveStart = effectiveStart(admissionTime, facilityZone);
        return boundariesPassed(effectiveStart, billingStop, facilityZone) + 1;
    }

    /**
     * §3.1: admission times in the 06:00-09:59 facility-local window bump
     * to that same day's 10:00 — "the grace is measured from actual
     * admission, not effective_start" (§3.3), so this is used only for the
     * boundary count, never for the 4-hour check above.
     */
    public static Instant effectiveStart(Instant admissionTime, ZoneId facilityZone) {
        ZonedDateTime local = admissionTime.atZone(facilityZone);
        LocalTime time = local.toLocalTime();
        if (!time.isBefore(MORNING_WINDOW_START) && time.isBefore(MORNING_WINDOW_END)) {
            return local.toLocalDate().atTime(MORNING_WINDOW_END).atZone(facilityZone).toInstant();
        }
        return admissionTime;
    }

    /** Count of 10:00 boundaries strictly after start and strictly before stop. */
    public static int boundariesPassed(Instant start, Instant stop, ZoneId facilityZone) {
        return boundariesBetween(start, stop, facilityZone).size();
    }

    /** The boundary timestamps themselves, strictly after start and strictly before stop. */
    public static List<Instant> boundariesBetween(Instant start, Instant stop, ZoneId facilityZone) {
        List<Instant> boundaries = new ArrayList<>();
        ZonedDateTime cursor = firstBoundaryStrictlyAfter(start, facilityZone);
        while (cursor.toInstant().isBefore(stop)) {
            boundaries.add(cursor.toInstant());
            cursor = cursor.plusDays(1);
        }
        return boundaries;
    }

    /**
     * The nightly job's catch-up sweep: 10:00 boundaries strictly after
     * lastPostedOrEffectiveStart and NOT AFTER now — i.e. boundaries that
     * have already occurred as of this run, inclusive of a boundary
     * occurring at exactly "now" (the job runs AT 10:00). Distinct from
     * boundariesBetween's strictly-before semantics, which apply to a
     * known billing_stop (a discharge time), not an ongoing "as of now."
     */
    public static List<Instant> boundariesToPost(Instant lastPostedOrEffectiveStart, Instant now, ZoneId facilityZone) {
        List<Instant> boundaries = new ArrayList<>();
        ZonedDateTime cursor = firstBoundaryStrictlyAfter(lastPostedOrEffectiveStart, facilityZone);
        while (!cursor.toInstant().isAfter(now)) {
            boundaries.add(cursor.toInstant());
            cursor = cursor.plusDays(1);
        }
        return boundaries;
    }

    private static ZonedDateTime firstBoundaryStrictlyAfter(Instant instant, ZoneId facilityZone) {
        LocalDate localDate = instant.atZone(facilityZone).toLocalDate();
        ZonedDateTime sameDayBoundary = localDate.atTime(MORNING_WINDOW_END).atZone(facilityZone);
        if (!sameDayBoundary.toInstant().isAfter(instant)) {
            return sameDayBoundary.plusDays(1);
        }
        return sameDayBoundary;
    }
}
