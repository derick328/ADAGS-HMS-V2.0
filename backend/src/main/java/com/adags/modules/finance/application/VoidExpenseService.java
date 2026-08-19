package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseRepository;
import com.adags.shared.authz.AuditRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 18-admin.md §8.2: "An approved expense is a financial record. delete
// should be VOID with a reason, retained." Reachable from any pre-VOID
// status, always with a reason — root CLAUDE.md "Data": nothing
// financial is ever hard-deleted, no DRAFT exception in this item's
// brief. Produces an audit_log row via the shared AuditRecorder, same
// mechanism every other module uses (root CLAUDE.md "Authorisation").
@Service
public class VoidExpenseService {

    private final ExpenseRepository expenseRepository;
    private final AuditRecorder auditRecorder;

    public VoidExpenseService(ExpenseRepository expenseRepository, AuditRecorder auditRecorder) {
        this.expenseRepository = expenseRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public Expense voidExpense(VoidExpenseCommand command) {
        if (command.voidReason() == null || command.voidReason().isBlank()) {
            throw new IllegalArgumentException("A void reason is required.");
        }
        Expense voided = expenseRepository.voidExpense(command.expenseId(), command.voidedBy(), command.voidReason());
        auditRecorder.record(command.voidedBy(), "EXPENSE_VOIDED", "EXPENSE", command.expenseId(), null);
        return voided;
    }
}
