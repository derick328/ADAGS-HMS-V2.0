package com.adags.modules.reporting.application;

import java.time.LocalDate;

// Invoice-level adjustments for a period: credit notes and write-offs.
//
// These exist as their own report precisely because they CANNOT be put on
// the category chart. Both are raised against an invoice, not an invoice
// line — credit_note.invoice_id and invoice_write_off.invoice_id are the
// only links either table has — and their ledger effect is a decrement of
// invoice.outstanding_total_minor (JdbcInvoiceRepository.recordCreditNote /
// recordWriteOff) that touches no line and no service item. Splitting one
// across the lines of its invoice would be an allocation nobody recorded,
// so this report states the totals and leaves them unattributed.
//
// Consequence, stated here so a consumer cannot miss it: per-category
// billed totals are GROSS of these adjustments. "Billed minus credit notes
// minus write-offs" is a valid facility-level net figure; the same
// subtraction per category is not, because the adjustment cannot be
// assigned to one.
//
// Only APPLIED rows count. PENDING has had no ledger effect yet and
// REJECTED never will — counting either would report money that moved
// nowhere. Pending counts are carried separately so an admin can see what
// is still coming.
//
// Dated by the decision, not the request: an adjustment belongs to the
// period in which it hit the ledger (credit_note.approved_at,
// invoice_write_off.decided_at).
public record FinancialAdjustments(
        LocalDate periodFrom,
        LocalDate periodTo,
        long creditNotesAppliedMinor,
        int creditNotesAppliedCount,
        long writeOffsAppliedMinor,
        int writeOffsAppliedCount,
        long refundsAppliedMinor,
        int pendingCreditNoteCount,
        int pendingWriteOffCount,
        String currencyCode
) {
    /** Total reduction applied to receivables in the period. Facility-level only — never per category. */
    public long totalAppliedMinor() {
        return creditNotesAppliedMinor + writeOffsAppliedMinor;
    }
}
