package com.adags.modules.finance.domain;

import com.adags.shared.kernel.Money;

import java.util.Optional;
import java.util.UUID;

public interface SalaryDisbursementBatchRepository {

    /** @throws DuplicateBatchException if (batchType, month, year) already has a row. */
    SalaryDisbursementBatch insert(SalaryDisbursementBatch batch);

    /**
     * GeneratePayrollBatchService inserts the batch first (empty totals —
     * the earliest possible point the (batchType, month, year) idempotency
     * check can fire, before any per-staff computation happens) and calls
     * this once every salary_record for it is written, in the same transaction.
     */
    SalaryDisbursementBatch updateTotals(UUID batchId, int headcount, Money grossTotal, Money deductionsTotal, Money netTotal);

    Optional<SalaryDisbursementBatch> findById(UUID id);

    Optional<SalaryDisbursementBatch> findByPeriod(String batchType, int month, int year);

    /**
     * PENDING -&gt; DISBURSED. The WHERE clause (not a prior read) is the
     * concurrency guard.
     *
     * @throws InvalidBatchTransitionException if the batch is not currently PENDING.
     */
    SalaryDisbursementBatch disburse(UUID batchId, UUID disbursedBy);

    /**
     * DISBURSED -&gt; REVERSED — the single reversible-batch action.
     *
     * @throws InvalidBatchTransitionException if the batch is not currently DISBURSED.
     */
    SalaryDisbursementBatch reverse(UUID batchId, UUID reversedBy, String reason);
}
