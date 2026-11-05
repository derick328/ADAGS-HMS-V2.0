package com.adags.modules.reporting.application;

import java.time.LocalDate;

// One bucket of the collection trend. bucketStart is the first day of the
// day/week/month bucket (facility-local date — the ::date casts below run
// in the session time zone, same as every other figure on these reports).
//
// Same two bases as CategoryRevenueLine: billed by invoice_line.created_at,
// collected by payment.received_at. Buckets with no activity are returned
// with zeros rather than omitted, so a line chart cannot imply a straight
// line across a gap that was actually a dead week.
public record RevenueTrendPoint(
        LocalDate bucketStart,
        long billedMinor,
        long collectedMinor,
        String currencyCode
) {
}
