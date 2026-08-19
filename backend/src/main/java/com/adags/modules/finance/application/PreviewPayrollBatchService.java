package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.DeductionRuleRepository;
import com.adags.modules.finance.domain.SalaryRecord;
import com.adags.modules.finance.domain.Staff;
import com.adags.modules.finance.domain.StaffRepository;
import com.adags.shared.kernel.Money;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// 18-admin.md §5.1: "Preview -> headcount, gross total, deductions
// total, net total, period." A read-only computation — nothing is
// written. GeneratePayrollBatchService commits exactly what this shows.
@Service
public class PreviewPayrollBatchService {

    private final StaffRepository staffRepository;
    private final DeductionRuleRepository deductionRuleRepository;

    public PreviewPayrollBatchService(StaffRepository staffRepository, DeductionRuleRepository deductionRuleRepository) {
        this.staffRepository = staffRepository;
        this.deductionRuleRepository = deductionRuleRepository;
    }

    public PayrollBatchPreview preview(int month, int year) {
        List<Staff> activeStaff = staffRepository.findAllActive();
        String currencyCode = "TZS";
        long grossTotal = 0;
        long deductionsTotal = 0;
        long netTotal = 0;
        List<PayrollBatchPreview.StaffLinePreview> lines = new ArrayList<>();

        for (Staff staff : activeStaff) {
            SalaryRecord computed = PayrollComputation.computeFor(staff, month, year, deductionRuleRepository, null);
            grossTotal += computed.gross().amountMinor();
            long staffDeductions = computed.gross().amountMinor() - computed.net().amountMinor();
            deductionsTotal += staffDeductions;
            netTotal += computed.net().amountMinor();
            lines.add(new PayrollBatchPreview.StaffLinePreview(staff.id(), staff.fullName(), computed.gross(),
                    new Money(staffDeductions, currencyCode), computed.net()));
        }

        return new PayrollBatchPreview(month, year, activeStaff.size(), new Money(grossTotal, currencyCode),
                new Money(deductionsTotal, currencyCode), new Money(netTotal, currencyCode), lines);
    }
}
