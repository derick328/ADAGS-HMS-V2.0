package com.adags.modules.finance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalaryRecordRepository {

    /**
     * @throws DuplicateSalaryRecordException if staffId already has a row for (month, year) — the
     *         partial unique index (staff_id, month, year) is the real guard against generating twice.
     */
    SalaryRecord insert(SalaryRecord record);

    Optional<SalaryRecord> findById(UUID id);

    List<SalaryRecord> findByBatchId(UUID batchId);

    /** Marks every record in batchId PAID — the batch-level disbursement action. */
    void markBatchPaid(UUID batchId, PaymentMethod paymentMethod);
}
