package com.adags.modules.finance;

import com.adags.modules.finance.application.GenerateRecurringExpensesService;
import com.adags.modules.finance.application.RecordExpenseCommand;
import com.adags.modules.finance.application.RecordExpenseService;
import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseFrequency;
import com.adags.modules.finance.domain.ExpenseRepository;
import com.adags.modules.finance.domain.PaymentMethod;
import com.adags.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 18-admin.md §8.3: "Recurring with idempotency guard on (template_id,
// period) — a monthly utility bill generated twice creates a duplicate
// liability." UNIQUE INDEX idx_expense_template_period (V48) is the real
// guard; GenerateRecurringExpensesService's existsByTemplateIdAndPeriod
// check is the ordinary, non-racing path onto it.
class RecurringExpenseGenerationTest extends IntegrationTestSupport {

    @Autowired
    private RecordExpenseService recordExpenseService;
    @Autowired
    private GenerateRecurringExpensesService generateRecurringExpensesService;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("TRUNCATE expense", new MapSqlParameterSource());
    }

    @Test
    void generatingTwiceForTheSamePeriodProducesOnlyOneOccurrence() {
        UUID creator = bootstrapAdminId();
        Expense template = recordExpenseService.record(new RecordExpenseCommand("Electricity", "Utilities",
                200_000, LocalDate.now(), PaymentMethod.BANK_TRANSFER, "TANESCO", null, null, null, true,
                ExpenseFrequency.MONTHLY, creator));

        List<Expense> firstRun = generateRecurringExpensesService.generate();
        List<Expense> secondRun = generateRecurringExpensesService.generate();

        assertThat(firstRun).hasSize(1);
        assertThat(firstRun.get(0).templateId()).isEqualTo(template.id());
        assertThat(secondRun).isEmpty();

        List<Expense> occurrences = expenseRepository.findByStatus(firstRun.get(0).status()).stream()
                .filter(e -> template.id().equals(e.templateId()))
                .toList();
        assertThat(occurrences).hasSize(1);
    }

    @Test
    void generatedOccurrencesAreFreshDraftsNeverAutoApproved() {
        UUID creator = bootstrapAdminId();
        recordExpenseService.record(new RecordExpenseCommand("Electricity", "Utilities", 200_000, LocalDate.now(),
                PaymentMethod.BANK_TRANSFER, "TANESCO", null, null, null, true, ExpenseFrequency.MONTHLY, creator));

        List<Expense> generated = generateRecurringExpensesService.generate();

        assertThat(generated).singleElement().satisfies(occurrence -> {
            assertThat(occurrence.status().name()).isEqualTo("DRAFT");
            assertThat(occurrence.recurring()).isFalse();
        });
    }

    // root CLAUDE.md "Concurrency": the partial unique index, not the
    // application-level existsByTemplateIdAndPeriod check, is what
    // actually prevents a duplicate liability under a race.
    @Test
    void theDatabaseRejectsADuplicateTemplatePeriodEvenBypassingTheServiceLayer() {
        UUID creator = bootstrapAdminId();
        Expense template = recordExpenseService.record(new RecordExpenseCommand("Electricity", "Utilities",
                200_000, LocalDate.now(), PaymentMethod.BANK_TRANSFER, "TANESCO", null, null, null, true,
                ExpenseFrequency.MONTHLY, creator));
        generateRecurringExpensesService.generate();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO expense (title, category, amount_minor, expense_date, payment_method, paid_to,
                                              recurring, template_id, period, created_by)
                        VALUES ('Electricity', 'Utilities', 200000, now(), 'BANK_TRANSFER', 'TANESCO',
                                FALSE, :templateId, :period, :createdBy)
                        """,
                new MapSqlParameterSource()
                        .addValue("templateId", template.id())
                        .addValue("period", String.format("%04d-%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue()))
                        .addValue("createdBy", creator)))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID bootstrapAdminId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = 'admin'", new MapSqlParameterSource(), UUID.class);
    }
}
