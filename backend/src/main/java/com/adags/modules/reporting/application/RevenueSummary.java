package com.adags.modules.reporting.application;

import java.time.LocalDate;

// The KPI row on /admin/revenue. Every field is defined against the ledger
// and labelled by basis, because the five numbers an admin wants on one
// line are deliberately not five views of one total:
//
//   collectedCashMinor        settled allocations received in the period,
//                             net of refunds. Cheques PENDING_CLEARANCE are
//                             excluded — not money until they clear.
//   grossBilledMinor          invoice_line.gross_minor for lines billed in
//                             the period (patient + insurance portion),
//                             excluding DRAFT and VOID invoices.
//   patientPortionBilledMinor the share of grossBilled the patient owes —
//                             the only part collectable as cash.
//   insuranceBilledMinor      the insurance share of lines billed in the
//                             period whose invoice has a claim raised.
//                             Billed, never collected.
//   refundedMinor             refunds applied to allocations, already
//                             netted out of collectedCashMinor; surfaced
//                             so the gross/net gap is visible.
//   outstandingTotalMinor     point-in-time, NOT period-bounded: what is
//                             owed now across post-discharge receivables
//                             and still-open encounters. Reflects credit
//                             notes and write-offs, which reduce
//                             invoice.outstanding_total_minor without
//                             touching any line.
//   settledInvoiceCount       invoices ISSUED in the period that now have
//                             nothing outstanding. A partially paid
//                             invoice is NOT counted.
//
//   cohortPatientPortionBilledMinor / cohortSettledMinor
//                             the collection rate's two terms, over ONE
//                             cohort: the lines billed in this period, and
//                             how much of their patient portion has been
//                             settled to date. Deliberately not
//                             collectedCash / patientPortionBilled — those
//                             two have different date bases, so their
//                             ratio is a figure that reconciles to nothing.
public record RevenueSummary(
        LocalDate periodFrom,
        LocalDate periodTo,
        long collectedCashMinor,
        long grossBilledMinor,
        long patientPortionBilledMinor,
        long insuranceBilledMinor,
        long refundedMinor,
        long outstandingTotalMinor,
        long postDischargeOutstandingMinor,
        long activeEncounterOutstandingMinor,
        int settledInvoiceCount,
        int issuedInvoiceCount,
        long cohortPatientPortionBilledMinor,
        long cohortSettledMinor,
        String currencyCode
) {
}
