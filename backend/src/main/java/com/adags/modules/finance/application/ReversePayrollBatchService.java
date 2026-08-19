package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.SalaryDisbursementBatch;
import com.adags.modules.finance.domain.SalaryDisbursementBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 18-admin.md §5.1: "single batch record, reversible as a batch" — the
// whole batch reverses at once, not individual salary records.
@Service
public class ReversePayrollBatchService {

    private final SalaryDisbursementBatchRepository batchRepository;

    public ReversePayrollBatchService(SalaryDisbursementBatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    @Transactional
    public SalaryDisbursementBatch reverse(ReversePayrollBatchCommand command) {
        if (command.reason() == null || command.reason().isBlank()) {
            throw new IllegalArgumentException("A reversal reason is required.");
        }
        return batchRepository.reverse(command.batchId(), command.reversedBy(), command.reason());
    }
}
