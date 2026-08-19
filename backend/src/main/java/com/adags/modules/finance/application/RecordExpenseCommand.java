package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.ExpenseFrequency;
import com.adags.modules.finance.domain.PaymentMethod;

import java.time.LocalDate;
import java.util.UUID;

public record RecordExpenseCommand(
        String title,
        String category,
        long amountMinor,
        LocalDate expenseDate,
        PaymentMethod paymentMethod,
        String paidTo,
        String receiptNumber,
        String notes,
        String receiptFileKey,
        boolean recurring,
        ExpenseFrequency frequency,
        UUID createdBy
) {
}
