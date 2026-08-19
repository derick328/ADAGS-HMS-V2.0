package com.adags.modules.finance.domain;

import com.adags.shared.kernel.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 18-admin.md §8.1. Pure domain logic, no Spring context (backend/CLAUDE.md "Testing").
class ExpenseTest {

    @Test
    void recordingAValidExpenseStartsAsDraft() {
        Expense expense = Expense.record("Diesel generator fuel", "Utilities", new Money(50_000, "TZS"),
                LocalDate.now(), PaymentMethod.CASH, "Total Energies", null, null, null, false, null, null, null,
                UUID.randomUUID());

        assertThat(expense.status()).isEqualTo(ExpenseStatus.DRAFT);
        assertThat(expense.id()).isNull();
        assertThat(expense.approvedBy()).isNull();
    }

    @Test
    void blankTitleIsRejected() {
        assertThatThrownBy(() -> Expense.record(" ", "Utilities", new Money(50_000, "TZS"), LocalDate.now(),
                PaymentMethod.CASH, "Total Energies", null, null, null, false, null, null, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        assertThatThrownBy(() -> Expense.record("Fuel", "Utilities", new Money(0, "TZS"), LocalDate.now(),
                PaymentMethod.CASH, "Total Energies", null, null, null, false, null, null, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recurringWithoutAFrequencyIsRejected() {
        assertThatThrownBy(() -> Expense.record("Fuel", "Utilities", new Money(50_000, "TZS"), LocalDate.now(),
                PaymentMethod.CASH, "Total Energies", null, null, null, true, null, null, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTemplateGeneratedOccurrenceWithoutAPeriodIsRejected() {
        assertThatThrownBy(() -> Expense.record("Fuel", "Utilities", new Money(50_000, "TZS"), LocalDate.now(),
                PaymentMethod.CASH, "Total Energies", null, null, null, false, null, UUID.randomUUID(), null,
                UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
