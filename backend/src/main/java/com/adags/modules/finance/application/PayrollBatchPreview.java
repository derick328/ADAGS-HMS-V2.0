package com.adags.modules.finance.application;

import com.adags.shared.kernel.Money;

import java.util.List;
import java.util.UUID;

// 18-admin.md §5.1: "Preview -> headcount, gross total, deductions
// total, net total, period." Pure read — nothing is written computing
// this; a payroll batch is only ever created by GeneratePayrollBatchService.
public record PayrollBatchPreview(
        int month,
        int year,
        int headcount,
        Money grossTotal,
        Money deductionsTotal,
        Money netTotal,
        List<StaffLinePreview> lines
) {

    public record StaffLinePreview(UUID staffId, String fullName, Money gross, Money deductions, Money net) {
    }
}
