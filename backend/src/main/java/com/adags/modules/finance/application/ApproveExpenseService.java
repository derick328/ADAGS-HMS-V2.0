package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseRepository;
import com.adags.modules.finance.domain.ExpenseSelfApprovalNotAllowedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// root CLAUDE.md "Authorisation": "CHECK (created_by != approved_by) on
// expenses" — "one person recording, approving, and paying is the
// standard embezzlement path." The database CHECK (V48) is the real
// guard; this is the friendly rejection before it ever reaches that.
@Service
public class ApproveExpenseService {

    private final ExpenseRepository expenseRepository;

    public ApproveExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Transactional
    public Expense approve(ApproveExpenseCommand command) {
        Expense expense = expenseRepository.findById(command.expenseId())
                .orElseThrow(() -> new NoSuchElementException("Expense not found: " + command.expenseId()));
        if (expense.createdBy().equals(command.approvedBy())) {
            throw new ExpenseSelfApprovalNotAllowedException();
        }
        return expenseRepository.approve(command.expenseId(), command.approvedBy(), command.approvalNote());
    }
}
