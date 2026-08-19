package com.adags.modules.finance.api;

import com.adags.modules.finance.domain.ExpenseFrequency;
import com.adags.modules.finance.domain.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecordExpenseRequest(
        @NotBlank String title,
        @NotBlank String category,
        @Min(1) long amountMinor,
        @NotNull LocalDate expenseDate,
        @NotNull PaymentMethod paymentMethod,
        @NotBlank String paidTo,
        String receiptNumber,
        String notes,
        String receiptFileKey,
        boolean recurring,
        ExpenseFrequency frequency
) {
}
