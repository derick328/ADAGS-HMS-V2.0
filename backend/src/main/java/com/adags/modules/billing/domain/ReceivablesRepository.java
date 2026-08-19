package com.adags.modules.billing.domain;

import java.util.List;

// Read-only report over RECEIVABLE_POST_DISCHARGE invoices — deliberately
// separate from InvoiceRepository, which owns the write-side entity.
public interface ReceivablesRepository {

    /** The aging matrix — one row per (payerCategory, bucket) combination present. */
    List<ReceivableSummary> summarizeAging();

    /**
     * The worklist behind the matrix. Either filter may be null to mean
     * "no filter on that dimension."
     */
    List<ReceivableInvoice> findReceivables(PayerCategory payerCategory, AgingBucket bucket);
}
