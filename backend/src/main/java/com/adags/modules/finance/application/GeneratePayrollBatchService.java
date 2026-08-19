package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.DeductionRuleRepository;
import com.adags.modules.finance.domain.SalaryDisbursementBatch;
import com.adags.modules.finance.domain.SalaryDisbursementBatchRepository;
import com.adags.modules.finance.domain.SalaryRecord;
import com.adags.modules.finance.domain.SalaryRecordRepository;
import com.adags.modules.finance.domain.Staff;
import com.adags.modules.finance.domain.StaffRepository;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// 18-admin.md §5.1: "Commit -> single batch record, reversible as a
// batch; idempotency key — running twice for one month must not pay
// twice." UNIQUE(batch_type, month, year) on salary_disbursement_batch
// is the real guard (root CLAUDE.md "Concurrency") — DuplicateBatchException
// surfaces a race or a naive retry, not a silent double-commit.
@Service
public class GeneratePayrollBatchService {

    private final StaffRepository staffRepository;
    private final DeductionRuleRepository deductionRuleRepository;
    private final SalaryRecordRepository salaryRecordRepository;
    private final SalaryDisbursementBatchRepository batchRepository;

    public GeneratePayrollBatchService(
            StaffRepository staffRepository,
            DeductionRuleRepository deductionRuleRepository,
            SalaryRecordRepository salaryRecordRepository,
            SalaryDisbursementBatchRepository batchRepository
    ) {
        this.staffRepository = staffRepository;
        this.deductionRuleRepository = deductionRuleRepository;
        this.salaryRecordRepository = salaryRecordRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional
    public SalaryDisbursementBatch generate(int month, int year, UUID generatedBy) {
        List<Staff> activeStaff = staffRepository.findAllActive();
        String currencyCode = "TZS";

        SalaryDisbursementBatch batch = batchRepository.insert(new SalaryDisbursementBatch(null,
                SalaryDisbursementBatch.SALARY_BATCH_TYPE, month, year, activeStaff.size(),
                new Money(0, currencyCode), new Money(0, currencyCode), new Money(0, currencyCode),
                null, generatedBy, null, null, null, null, null, null));

        long grossTotal = 0;
        long deductionsTotal = 0;
        long netTotal = 0;
        for (Staff staff : activeStaff) {
            SalaryRecord computed = PayrollComputation.computeFor(staff, month, year, deductionRuleRepository, batch.id());
            salaryRecordRepository.insert(computed);
            grossTotal += computed.gross().amountMinor();
            netTotal += computed.net().amountMinor();
            deductionsTotal += computed.gross().amountMinor() - computed.net().amountMinor();
        }

        return refreshTotals(batch.id(), activeStaff.size(), grossTotal, deductionsTotal, netTotal, currencyCode);
    }

    private SalaryDisbursementBatch refreshTotals(UUID batchId, int headcount, long grossTotal, long deductionsTotal,
                                                    long netTotal, String currencyCode) {
        // The batch row's totals were inserted as zero (nothing was
        // computed yet at insert time, which is also what earns the
        // idempotency check its atomic first move); update them now that
        // every salary_record is written, still inside the same transaction.
        return batchRepository.updateTotals(batchId, headcount, new Money(grossTotal, currencyCode),
                new Money(deductionsTotal, currencyCode), new Money(netTotal, currencyCode));
    }
}
