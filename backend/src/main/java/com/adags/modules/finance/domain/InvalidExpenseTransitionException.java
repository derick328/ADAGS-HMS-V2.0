package com.adags.modules.finance.domain;

import java.util.UUID;

public class InvalidExpenseTransitionException extends RuntimeException {

    public InvalidExpenseTransitionException(UUID expenseId, ExpenseStatus from, String action) {
        super("Cannot " + action + " expense " + expenseId + " — it is " + from + ".");
    }
}
