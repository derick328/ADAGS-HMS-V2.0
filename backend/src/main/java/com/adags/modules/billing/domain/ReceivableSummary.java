package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

// One cell of the aged-receivables matrix — payer category x aging bucket.
public record ReceivableSummary(
        PayerCategory payerCategory,
        AgingBucket bucket,
        int invoiceCount,
        Money totalOutstanding
) {
}
