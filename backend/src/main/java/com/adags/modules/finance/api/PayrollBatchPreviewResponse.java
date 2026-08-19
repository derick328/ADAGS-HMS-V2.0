package com.adags.modules.finance.api;

import com.adags.modules.finance.application.PayrollBatchPreview;

import java.util.List;
import java.util.UUID;

public record PayrollBatchPreviewResponse(
        int month,
        int year,
        int headcount,
        long grossTotalMinor,
        long deductionsTotalMinor,
        long netTotalMinor,
        String currencyCode,
        List<StaffLine> lines
) {

    public record StaffLine(UUID staffId, String fullName, long grossMinor, long deductionsMinor, long netMinor) {
    }

    public static PayrollBatchPreviewResponse from(PayrollBatchPreview preview) {
        List<StaffLine> lines = preview.lines().stream()
                .map(l -> new StaffLine(l.staffId(), l.fullName(), l.gross().amountMinor(),
                        l.deductions().amountMinor(), l.net().amountMinor()))
                .toList();
        return new PayrollBatchPreviewResponse(preview.month(), preview.year(), preview.headcount(),
                preview.grossTotal().amountMinor(), preview.deductionsTotal().amountMinor(),
                preview.netTotal().amountMinor(), preview.grossTotal().currencyCode(), lines);
    }
}
