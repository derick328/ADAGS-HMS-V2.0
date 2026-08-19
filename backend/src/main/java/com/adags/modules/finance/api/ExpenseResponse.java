package com.adags.modules.finance.api;

import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseFrequency;
import com.adags.modules.finance.domain.ExpenseStatus;
import com.adags.modules.finance.domain.PaymentMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        String title,
        String category,
        long amountMinor,
        String currencyCode,
        LocalDate expenseDate,
        PaymentMethod paymentMethod,
        String paidTo,
        String receiptNumber,
        String notes,
        String receiptFileKey,
        boolean recurring,
        ExpenseFrequency frequency,
        UUID templateId,
        String period,
        ExpenseStatus status,
        UUID createdBy,
        Instant createdAt,
        UUID approvedBy,
        Instant approvedAt,
        String approvalNote,
        UUID voidedBy,
        Instant voidedAt,
        String voidReason
) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(expense.id(), expense.title(), expense.category(), expense.amount().amountMinor(),
                expense.amount().currencyCode(), expense.expenseDate(), expense.paymentMethod(), expense.paidTo(),
                expense.receiptNumber(), expense.notes(), expense.receiptFileKey(), expense.recurring(),
                expense.frequency(), expense.templateId(), expense.period(), expense.status(), expense.createdBy(),
                expense.createdAt(), expense.approvedBy(), expense.approvedAt(), expense.approvalNote(),
                expense.voidedBy(), expense.voidedAt(), expense.voidReason());
    }
}
