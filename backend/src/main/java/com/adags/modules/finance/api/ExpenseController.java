package com.adags.modules.finance.api;

import com.adags.modules.finance.application.ApproveExpenseCommand;
import com.adags.modules.finance.application.ApproveExpenseService;
import com.adags.modules.finance.application.RecordExpenseCommand;
import com.adags.modules.finance.application.RecordExpenseService;
import com.adags.modules.finance.application.VoidExpenseCommand;
import com.adags.modules.finance.application.VoidExpenseService;
import com.adags.modules.finance.application.WithdrawExpenseService;
import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseRepository;
import com.adags.modules.finance.domain.ExpenseStatus;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 18-admin.md §8: EXPENSE_CREATE (record/submit/withdraw), EXPENSE_APPROVE
// (approve/void — both already granted to ADMIN and ACCOUNTANT, V5).
@RestController
@RequestMapping("/api/finance/expenses")
public class ExpenseController {

    private final RecordExpenseService recordExpenseService;
    private final ApproveExpenseService approveExpenseService;
    private final VoidExpenseService voidExpenseService;
    private final WithdrawExpenseService withdrawExpenseService;
    private final ExpenseRepository expenseRepository;
    private final AuthorizationService authorizationService;

    public ExpenseController(
            RecordExpenseService recordExpenseService,
            ApproveExpenseService approveExpenseService,
            VoidExpenseService voidExpenseService,
            WithdrawExpenseService withdrawExpenseService,
            ExpenseRepository expenseRepository,
            AuthorizationService authorizationService
    ) {
        this.recordExpenseService = recordExpenseService;
        this.approveExpenseService = approveExpenseService;
        this.voidExpenseService = voidExpenseService;
        this.withdrawExpenseService = withdrawExpenseService;
        this.expenseRepository = expenseRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse record(@Valid @RequestBody RecordExpenseRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "EXPENSE_CREATE");
        Expense expense = recordExpenseService.record(new RecordExpenseCommand(request.title(), request.category(),
                request.amountMinor(), request.expenseDate(), request.paymentMethod(), request.paidTo(),
                request.receiptNumber(), request.notes(), request.receiptFileKey(), request.recurring(),
                request.frequency(), actor.userId()));
        return ExpenseResponse.from(expense);
    }

    @GetMapping("/{id}")
    public ExpenseResponse findById(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "EXPENSE_CREATE");
        return expenseRepository.findById(id)
                .map(ExpenseResponse::from)
                .orElseThrow(() -> new NoSuchElementException("Expense not found: " + id));
    }

    @GetMapping
    public List<ExpenseResponse> findByStatus(@RequestParam ExpenseStatus status, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "EXPENSE_CREATE");
        return expenseRepository.findByStatus(status).stream().map(ExpenseResponse::from).toList();
    }

    @PostMapping("/{id}/submit")
    public ExpenseResponse submit(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "EXPENSE_CREATE");
        return ExpenseResponse.from(expenseRepository.submitForApproval(id));
    }

    @PostMapping("/{id}/withdraw")
    public ExpenseResponse withdraw(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "EXPENSE_CREATE");
        return ExpenseResponse.from(withdrawExpenseService.withdraw(id));
    }

    @PostMapping("/{id}/approve")
    public ExpenseResponse approve(
            @PathVariable UUID id, @Valid @RequestBody ApproveExpenseRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "EXPENSE_APPROVE");
        Expense approved = approveExpenseService.approve(new ApproveExpenseCommand(id, actor.userId(), request.approvalNote()));
        return ExpenseResponse.from(approved);
    }

    @PostMapping("/{id}/void")
    public ExpenseResponse voidExpense(
            @PathVariable UUID id, @Valid @RequestBody VoidExpenseRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "EXPENSE_APPROVE");
        Expense voided = voidExpenseService.voidExpense(new VoidExpenseCommand(id, actor.userId(), request.voidReason()));
        return ExpenseResponse.from(voided);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
