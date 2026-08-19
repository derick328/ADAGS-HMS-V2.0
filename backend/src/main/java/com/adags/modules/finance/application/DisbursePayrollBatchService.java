package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.BatchSelfDisbursementNotAllowedException;
import com.adags.modules.finance.domain.SalaryDisbursementBatch;
import com.adags.modules.finance.domain.SalaryDisbursementBatchRepository;
import com.adags.modules.finance.domain.SalaryRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// root CLAUDE.md "Authorisation": "CHECK (generated_by != disbursed_by)"
// — the database CHECK (V49) is the real guard; this is the friendly
// rejection before it ever reaches that constraint.
@Service
public class DisbursePayrollBatchService {

    private final SalaryDisbursementBatchRepository batchRepository;
    private final SalaryRecordRepository salaryRecordRepository;

    public DisbursePayrollBatchService(SalaryDisbursementBatchRepository batchRepository,
                                        SalaryRecordRepository salaryRecordRepository) {
        this.batchRepository = batchRepository;
        this.salaryRecordRepository = salaryRecordRepository;
    }

    @Transactional
    public SalaryDisbursementBatch disburse(DisbursePayrollBatchCommand command) {
        SalaryDisbursementBatch batch = batchRepository.findById(command.batchId())
                .orElseThrow(() -> new NoSuchElementException("Payroll batch not found: " + command.batchId()));
        if (batch.generatedBy().equals(command.disbursedBy())) {
            throw new BatchSelfDisbursementNotAllowedException();
        }
        SalaryDisbursementBatch disbursed = batchRepository.disburse(command.batchId(), command.disbursedBy());
        salaryRecordRepository.markBatchPaid(command.batchId(), command.paymentMethod());
        return disbursed;
    }
}
