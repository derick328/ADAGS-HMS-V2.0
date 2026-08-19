package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseRepository;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 18-admin.md §8.1: "As described: title, category, amount, date,
// payment method, paid-to, receipt number, notes, recurring with
// frequency, receipt file upload." Always a DRAFT — freely editable,
// not yet a financial record, same two-step reasoning as Invoice.draft().
@Service
public class RecordExpenseService {

    private final ExpenseRepository expenseRepository;

    public RecordExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Transactional
    public Expense record(RecordExpenseCommand command) {
        Expense expense = Expense.record(command.title(), command.category(),
                new Money(command.amountMinor(), "TZS"), command.expenseDate(), command.paymentMethod(),
                command.paidTo(), command.receiptNumber(), command.notes(), command.receiptFileKey(),
                command.recurring(), command.frequency(), null, null, command.createdBy());
        return expenseRepository.insert(expense);
    }
}
