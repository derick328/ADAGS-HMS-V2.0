package com.adags.modules.reporting.application;

// One revenue category over a period. billed and collected are BOTH period
// flows but on their own natural event date, which is the convention
// RevenueReportService already uses and the only one that doesn't invent a
// number:
//
//   billedMinor    — invoice_line.gross_minor for lines CREATED in the
//                    period, on invoices that are neither DRAFT nor VOID.
//   collectedMinor — settled payment allocations RECEIVED in the period,
//                    net of refunds, attributed to the line's category.
//                    A payment received this month against a line billed
//                    last month counts here, not in last month's billed.
//
// They are therefore not two views of the same money and must never be
// subtracted from each other to produce "outstanding" — invoice
// .outstanding_total_minor is the only figure that means that.
//
// insuranceBilledMinor is the insurance portion of lines billed in the
// period that have a claim raised against their invoice — billed, never
// collected (02-billing-architecture.md §13).
public record CategoryRevenueLine(
        RevenueCategory category,
        String categoryLabel,
        long billedMinor,
        long collectedMinor,
        long insuranceBilledMinor,
        int billedLineCount,
        String currencyCode
) {
}
