package com.adags.modules.reporting.application;

// root CLAUDE.md "Billing": "Never block discharge or encounter closure
// on an unpaid balance... if balance > 0 -> create receivable." The two
// figures here are deliberately not summed: activeEncounterOutstanding
// is a balance on a patient who is still there and still governed by
// the billing gate — normal, not yet chase-worthy. postDischargeOutstanding
// is what item 1's aged-receivables view already covers exclusively
// (ReceivablesRepository queries only state = 'RECEIVABLE_POST_DISCHARGE')
// — this record makes that existing distinction visible in one place
// rather than leaving it implicit in which rows a query happens to
// return.
public record ReceivablesOverview(
        long postDischargeOutstandingMinor,
        int postDischargeInvoiceCount,
        long activeEncounterOutstandingMinor,
        int activeEncounterInvoiceCount,
        String currencyCode
) {
}
