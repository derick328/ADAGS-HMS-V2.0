package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SubmitExpenseForApprovalService {

    private final ExpenseRepository expenseRepository;

    public SubmitExpenseForApprovalService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Transactional
    public Expense submit(UUID expenseId) {
        return expenseRepository.submitForApproval(expenseId);
    }
}
