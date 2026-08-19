package com.adags.modules.reporting;

import com.adags.modules.finance.application.ApproveExpenseCommand;
import com.adags.modules.finance.application.ApproveExpenseService;
import com.adags.modules.finance.application.RecordExpenseCommand;
import com.adags.modules.finance.application.RecordExpenseService;
import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseRepository;
import com.adags.modules.finance.domain.PaymentMethod;
import com.adags.modules.reporting.application.ExpenseCategoryTotal;
import com.adags.modules.reporting.application.ExpenseMonthlyTotal;
import com.adags.modules.reporting.application.ExpenseReportService;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 18-admin.md §5: "by category, monthly trend."
class ExpenseReportServiceTest extends IntegrationTestSupport {

    @Autowired
    private ExpenseReportService expenseReportService;
    @Autowired
    private RecordExpenseService recordExpenseService;
    @Autowired
    private ApproveExpenseService approveExpenseService;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE expense", new MapSqlParameterSource());
    }

    @Test
    void onlyApprovedExpensesCountTowardTheCategoryTotal() {
        UUID creator = bootstrapAdminId();
        UUID approver = insertFixtureUser();
        approve(record("Fuel", "Utilities", 50_000, creator), approver);
        record("Stationery", "Utilities", 20_000, creator); // left DRAFT — must not count

        List<ExpenseCategoryTotal> totals = expenseReportService.byCategory(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

        assertThat(totals).hasSize(1);
        assertThat(totals.get(0).category()).isEqualTo("Utilities");
        assertThat(totals.get(0).totalMinor()).isEqualTo(50_000);
    }

    @Test
    void categoriesAreSummedSeparately() {
        UUID creator = bootstrapAdminId();
        UUID approver = insertFixtureUser();
        approve(record("Fuel", "Utilities", 50_000, creator), approver);
        approve(record("Gloves", "Medical Supplies", 30_000, creator), approver);

        List<ExpenseCategoryTotal> totals = expenseReportService.byCategory(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

        assertThat(totals).hasSize(2);
        assertThat(totals.stream().mapToLong(ExpenseCategoryTotal::totalMinor).sum()).isEqualTo(80_000);
    }

    @Test
    void monthlyTrendGroupsByYearAndMonth() {
        UUID creator = bootstrapAdminId();
        UUID approver = insertFixtureUser();
        Expense recorded = record("Fuel", "Utilities", 50_000, creator);
        approve(recorded, approver);
        jdbcTemplate.update("UPDATE expense SET expense_date = :date WHERE id = :id",
                new MapSqlParameterSource().addValue("date", LocalDate.now().withDayOfMonth(1)).addValue("id", recorded.id()));

        List<ExpenseMonthlyTotal> trend = expenseReportService.monthlyTrend(
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1));

        assertThat(trend).hasSize(1);
        assertThat(trend.get(0).year()).isEqualTo(LocalDate.now().getYear());
        assertThat(trend.get(0).month()).isEqualTo(LocalDate.now().getMonthValue());
        assertThat(trend.get(0).totalMinor()).isEqualTo(50_000);
    }

    @Test
    void aVoidedExpenseIsExcludedEvenIfItWasPreviouslyApproved() {
        UUID creator = bootstrapAdminId();
        UUID approver = insertFixtureUser();
        Expense approved = approve(record("Fuel", "Utilities", 50_000, creator), approver);
        expenseRepository.voidExpense(approved.id(), approver, "posted in error");

        List<ExpenseCategoryTotal> totals = expenseReportService.byCategory(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

        assertThat(totals).isEmpty();
    }

    private Expense record(String title, String category, long amountMinor, UUID createdBy) {
        return recordExpenseService.record(new RecordExpenseCommand(title, category, amountMinor, LocalDate.now(),
                PaymentMethod.CASH, "Test Vendor", null, null, null, false, null, createdBy));
    }

    private Expense approve(Expense expense, UUID approver) {
        expenseRepository.submitForApproval(expense.id());
        return approveExpenseService.approve(new ApproveExpenseCommand(expense.id(), approver, "ok"));
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
