package com.adags.modules.finance;

import com.adags.modules.finance.application.ApproveExpenseCommand;
import com.adags.modules.finance.application.ApproveExpenseService;
import com.adags.modules.finance.application.RecordExpenseCommand;
import com.adags.modules.finance.application.RecordExpenseService;
import com.adags.modules.finance.application.VoidExpenseCommand;
import com.adags.modules.finance.application.VoidExpenseService;
import com.adags.modules.finance.application.WithdrawExpenseService;
import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseFrequency;
import com.adags.modules.finance.domain.ExpenseRepository;
import com.adags.modules.finance.domain.ExpenseSelfApprovalNotAllowedException;
import com.adags.modules.finance.domain.ExpenseStatus;
import com.adags.modules.finance.domain.InvalidExpenseTransitionException;
import com.adags.modules.finance.domain.PaymentMethod;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Phase 7 item 4: DRAFT -> PENDING_APPROVAL -> APPROVED, VOID reachable
// from any pre-VOID status with a reason. root CLAUDE.md "Authorisation":
// CHECK (approved_by != created_by) — "one person recording, approving,
// and paying is the standard embezzlement path."
class ExpenseLifecycleTest extends IntegrationTestSupport {

    @Autowired
    private RecordExpenseService recordExpenseService;
    @Autowired
    private ApproveExpenseService approveExpenseService;
    @Autowired
    private VoidExpenseService voidExpenseService;
    @Autowired
    private WithdrawExpenseService withdrawExpenseService;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE expense", new MapSqlParameterSource());
    }

    @Test
    void aRecordedExpenseCanBeSubmittedApprovedByADistinctUser() {
        UUID creator = bootstrapAdminId();
        UUID approver = insertFixtureUser();

        Expense recorded = recordExpenseService.record(fuelCommand(creator));
        assertThat(recorded.status()).isEqualTo(ExpenseStatus.DRAFT);

        expenseRepository.submitForApproval(recorded.id());

        Expense approved = approveExpenseService.approve(
                new ApproveExpenseCommand(recorded.id(), approver, "confirmed against receipt"));

        assertThat(approved.status()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo(approver);
    }

    @Test
    void theCreatorCannotApproveTheirOwnExpense() {
        UUID creator = bootstrapAdminId();
        Expense recorded = recordExpenseService.record(fuelCommand(creator));
        expenseRepository.submitForApproval(recorded.id());

        assertThatThrownBy(() -> approveExpenseService.approve(
                new ApproveExpenseCommand(recorded.id(), creator, "self-approving")))
                .isInstanceOf(ExpenseSelfApprovalNotAllowedException.class);

        Expense reloaded = expenseRepository.findById(recorded.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ExpenseStatus.PENDING_APPROVAL);
    }

    // root CLAUDE.md "Authorisation": the database CHECK is the real
    // guard, proven by going around the service entirely.
    @Test
    void theDatabaseRejectsSelfApprovalEvenBypassingTheServiceLayer() {
        UUID creator = bootstrapAdminId();
        Expense recorded = recordExpenseService.record(fuelCommand(creator));
        expenseRepository.submitForApproval(recorded.id());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE expense SET status = 'APPROVED', approved_by = :approver, approved_at = now()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource().addValue("id", recorded.id()).addValue("approver", creator)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void withdrawingReturnsAPendingExpenseToDraft() {
        UUID creator = bootstrapAdminId();
        Expense recorded = recordExpenseService.record(fuelCommand(creator));
        expenseRepository.submitForApproval(recorded.id());

        Expense withdrawn = withdrawExpenseService.withdraw(recorded.id());

        assertThat(withdrawn.status()).isEqualTo(ExpenseStatus.DRAFT);
    }

    @Test
    void voidingRequiresAReason() {
        UUID creator = bootstrapAdminId();
        Expense recorded = recordExpenseService.record(fuelCommand(creator));

        assertThatThrownBy(() -> voidExpenseService.voidExpense(
                new VoidExpenseCommand(recorded.id(), creator, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> voidExpenseService.voidExpense(
                new VoidExpenseCommand(recorded.id(), creator, "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 18-admin.md §8.2: VOID is reachable from any pre-VOID status, not
    // only APPROVED — the highest-stakes case does not carve out an
    // exception for DRAFT.
    @Test
    void aDraftExpenseCanBeVoidedDirectly() {
        UUID creator = bootstrapAdminId();
        Expense recorded = recordExpenseService.record(fuelCommand(creator));

        Expense voided = voidExpenseService.voidExpense(
                new VoidExpenseCommand(recorded.id(), creator, "duplicate entry"));

        assertThat(voided.status()).isEqualTo(ExpenseStatus.VOID);
        assertThat(voided.voidReason()).isEqualTo("duplicate entry");
    }

    @Test
    void anApprovedExpenseCanStillBeVoided() {
        UUID creator = bootstrapAdminId();
        UUID approver = insertFixtureUser();
        Expense recorded = recordExpenseService.record(fuelCommand(creator));
        expenseRepository.submitForApproval(recorded.id());
        approveExpenseService.approve(new ApproveExpenseCommand(recorded.id(), approver, "ok"));

        Expense voided = voidExpenseService.voidExpense(
                new VoidExpenseCommand(recorded.id(), approver, "posted in error"));

        assertThat(voided.status()).isEqualTo(ExpenseStatus.VOID);
    }

    @Test
    void voidingAnAlreadyVoidExpenseIsRejected() {
        UUID creator = bootstrapAdminId();
        Expense recorded = recordExpenseService.record(fuelCommand(creator));
        voidExpenseService.voidExpense(new VoidExpenseCommand(recorded.id(), creator, "first void"));

        assertThatThrownBy(() -> voidExpenseService.voidExpense(
                new VoidExpenseCommand(recorded.id(), creator, "second void")))
                .isInstanceOf(InvalidExpenseTransitionException.class);
    }

    private RecordExpenseCommand fuelCommand(UUID createdBy) {
        return new RecordExpenseCommand("Diesel generator fuel", "Utilities", 50_000, LocalDate.now(),
                PaymentMethod.CASH, "Total Energies", "RCT-001", null, null, false, (ExpenseFrequency) null, createdBy);
    }

    private UUID insertFixtureUser() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, password_hash) VALUES (:username, 'x') RETURNING id",
                new MapSqlParameterSource("username", "test-approver-" + UUID.randomUUID()), UUID.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
