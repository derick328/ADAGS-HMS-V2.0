package com.adags.modules.finance.api;

import com.adags.modules.finance.domain.BatchStatus;
import com.adags.modules.finance.domain.SalaryDisbursementBatch;

import java.time.Instant;
import java.util.UUID;

public record SalaryDisbursementBatchResponse(
        UUID id,
        String batchType,
        int month,
        int year,
        int headcount,
        long grossTotalMinor,
        long deductionsTotalMinor,
        long netTotalMinor,
        String currencyCode,
        BatchStatus status,
        UUID generatedBy,
        Instant generatedAt,
        UUID disbursedBy,
        Instant disbursedAt,
        UUID reversedBy,
        Instant reversedAt,
        String reversalReason
) {

    public static SalaryDisbursementBatchResponse from(SalaryDisbursementBatch batch) {
        return new SalaryDisbursementBatchResponse(batch.id(), batch.batchType(), batch.month(), batch.year(),
                batch.headcount(), batch.grossTotal().amountMinor(), batch.deductionsTotal().amountMinor(),
                batch.netTotal().amountMinor(), batch.grossTotal().currencyCode(), batch.status(),
                batch.generatedBy(), batch.generatedAt(), batch.disbursedBy(), batch.disbursedAt(),
                batch.reversedBy(), batch.reversedAt(), batch.reversalReason());
    }
}
