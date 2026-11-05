package com.adags.modules.reporting.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// Bucket width for the collection trend. The bucket boundaries are computed
// here in Java and in Postgres by date_trunc over the same unit, so a
// bucket the query returns always has a slot waiting for it — and a period
// with no activity still gets a point at zero rather than vanishing from
// the line.
//
// WEEK follows Postgres date_trunc('week', ...): ISO weeks starting Monday.
// Computing the first bucket any other way would leave the query's Monday
// rows with nowhere to land.
public enum TrendGranularity {
    DAY {
        @Override
        LocalDate floor(LocalDate date) {
            return date;
        }

        @Override
        LocalDate next(LocalDate bucket) {
            return bucket.plusDays(1);
        }
    },
    WEEK {
        @Override
        LocalDate floor(LocalDate date) {
            return date.with(DayOfWeek.MONDAY);
        }

        @Override
        LocalDate next(LocalDate bucket) {
            return bucket.plusWeeks(1);
        }
    },
    MONTH {
        @Override
        LocalDate floor(LocalDate date) {
            return date.withDayOfMonth(1);
        }

        @Override
        LocalDate next(LocalDate bucket) {
            return bucket.plusMonths(1);
        }
    };

    abstract LocalDate floor(LocalDate date);

    abstract LocalDate next(LocalDate bucket);

    public String sqlUnit() {
        return name().toLowerCase();
    }

    public List<LocalDate> buckets(LocalDate from, LocalDate to) {
        List<LocalDate> buckets = new ArrayList<>();
        for (LocalDate bucket = floor(from); !bucket.isAfter(to); bucket = next(bucket)) {
            buckets.add(bucket);
        }
        return buckets;
    }

    public int bucketCount(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(floor(from), to) + 1;
        return switch (this) {
            case DAY -> (int) days;
            case WEEK -> (int) Math.ceil(days / 7.0);
            case MONTH -> (int) (ChronoUnit.MONTHS.between(floor(from), floor(to)) + 1);
        };
    }

    /** What a given span should default to when the caller has no opinion. */
    public static TrendGranularity forSpan(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 31) return DAY;
        if (days <= 182) return WEEK;
        return MONTH;
    }
}
