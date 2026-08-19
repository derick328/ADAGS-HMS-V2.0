package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.Expense;
import com.adags.modules.finance.domain.ExpenseFrequency;
import com.adags.modules.finance.domain.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 18-admin.md §8.3: "Recurring generation needs an idempotency guard — a
// monthly utility bill generated twice creates a duplicate liability.
// Key on (expense_template_id, period)." A recurring=TRUE row is its own
// template; each occurrence is a fresh DRAFT expense (never auto-approved
// — the created_by != approved_by separation must still hold every
// period, not just once) carrying templateId/period back to it.
@Service
public class GenerateRecurringExpensesService {

    private final ExpenseRepository expenseRepository;
    private final Clock clock;

    public GenerateRecurringExpensesService(ExpenseRepository expenseRepository, Clock clock) {
        this.expenseRepository = expenseRepository;
        this.clock = clock;
    }

    @Transactional
    public List<Expense> generate() {
        LocalDate today = LocalDate.now(clock);
        List<Expense> generated = new ArrayList<>();
        for (Expense template : expenseRepository.findActiveRecurringTemplates()) {
            String period = periodFor(template.frequency(), today);
            if (expenseRepository.existsByTemplateIdAndPeriod(template.id(), period)) {
                continue;
            }
            Expense occurrence = Expense.record(template.title(), template.category(), template.amount(), today,
                    template.paymentMethod(), template.paidTo(), null, template.notes(), null,
                    false, null, template.id(), period, template.createdBy());
            generated.add(expenseRepository.insert(occurrence));
        }
        return generated;
    }

    private String periodFor(ExpenseFrequency frequency, LocalDate date) {
        return switch (frequency) {
            case MONTHLY -> String.format("%04d-%02d", date.getYear(), date.getMonthValue());
            case QUARTERLY -> date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case ANNUALLY -> String.valueOf(date.getYear());
        };
    }
}
