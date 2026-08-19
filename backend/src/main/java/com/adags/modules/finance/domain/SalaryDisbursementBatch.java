package com.adags.modules.finance.domain;

import com.adags.shared.kernel.Money;

import java.time.Instant;
import java.util.UUID;

// 18-admin.md §5.1: "Bulk-disburse is the largest single financial
// action in the system. It shows what it is about to do before it does
// it" — headcount/gross/deductions/net are the preview's numbers,
// persisted here once generate() commits so the batch stays
// self-describing. "Single reversible batch" — reverse() undoes the
// whole batch at once, not individual salary records.
public record SalaryDisbursementBatch(
        UUID id,
        String batchType,
        int month,
        int year,
        int headcount,
        Money grossTotal,
        Money deductionsTotal,
        Money netTotal,
        BatchStatus status,
        UUID generatedBy,
        Instant generatedAt,
        UUID disbursedBy,
        Instant disbursedAt,
        UUID reversedBy,
        Instant reversedAt,
        String reversalReason
) {

    public static final String SALARY_BATCH_TYPE = "SALARY";
}
