package com.adags.modules.billing.domain;

// 18-admin.md (Phase 7 item 1): "Aged receivables view: current, 30, 60,
// 90+, by payer category." Bucketed from Invoice.receivableAt — the day the
// balance survived discharge, not the invoice's original issue date.
public enum AgingBucket {
    CURRENT,
    DAYS_30,
    DAYS_60,
    DAYS_90_PLUS;

    public static AgingBucket fromAgingDays(long agingDays) {
        if (agingDays < 30) {
            return CURRENT;
        }
        if (agingDays < 60) {
            return DAYS_30;
        }
        if (agingDays < 90) {
            return DAYS_60;
        }
        return DAYS_90_PLUS;
    }
}
