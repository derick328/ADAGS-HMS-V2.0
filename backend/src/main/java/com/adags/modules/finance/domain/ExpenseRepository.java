package com.adags.modules.finance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository {

    Expense insert(Expense expense);

    Optional<Expense> findById(UUID id);

    List<Expense> findByStatus(ExpenseStatus status);

    /** Every active (recurring, non-voided) template — what GenerateRecurringExpensesService scans. */
    List<Expense> findActiveRecurringTemplates();

    boolean existsByTemplateIdAndPeriod(UUID templateId, String period);

    /**
     * DRAFT -&gt; PENDING_APPROVAL, ISSUED... no — DRAFT only. The WHERE
     * clause (not a prior read) is the concurrency guard.
     *
     * @throws InvalidExpenseTransitionException if the expense is not currently DRAFT.
     */
    Expense submitForApproval(UUID expenseId);

    /**
     * PENDING_APPROVAL -&gt; DRAFT — the creator withdraws it for editing.
     *
     * @throws InvalidExpenseTransitionException if the expense is not currently PENDING_APPROVAL.
     */
    Expense withdraw(UUID expenseId);

    /**
     * PENDING_APPROVAL -&gt; APPROVED. approvedBy != createdBy is enforced
     * by the database CHECK, not just the WHERE clause here.
     *
     * @throws InvalidExpenseTransitionException if the expense is not currently PENDING_APPROVAL.
     */
    Expense approve(UUID expenseId, UUID approvedBy, String approvalNote);

    /**
     * Any pre-VOID status -&gt; VOID, reason required (root CLAUDE.md
     * "Data": nothing financial is ever hard-deleted).
     *
     * @throws InvalidExpenseTransitionException if the expense is already VOID.
     */
    Expense voidExpense(UUID expenseId, UUID voidedBy, String voidReason);
}
