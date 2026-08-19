package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.AgingBucket;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.ReceivableSummary;

public record AgingSummaryResponse(
        PayerCategory payerCategory,
        AgingBucket bucket,
        int invoiceCount,
        long totalOutstandingMinor,
        String currencyCode
) {

    public static AgingSummaryResponse from(ReceivableSummary summary) {
        return new AgingSummaryResponse(summary.payerCategory(), summary.bucket(), summary.invoiceCount(),
                summary.totalOutstanding().amountMinor(), summary.totalOutstanding().currencyCode());
    }
}
