package com.adags.modules.finance.infrastructure;

import com.adags.modules.finance.application.GenerateRecurringExpensesService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 18-admin.md §8.3: recurring expense generation. Daily, not monthly —
// templates can be MONTHLY, QUARTERLY or ANNUALLY, so only checking once
// a month would miss quarterly/annual boundaries; the (templateId,
// period) idempotency guard makes a daily no-op check cheap and safe on
// every day that isn't a period boundary. Same facility-local-hour
// reasoning as WardChargeAccrualJob/OutstandingBalanceReconciliationJob.
@Component
public class GenerateRecurringExpensesJob {

    private final GenerateRecurringExpensesService generateRecurringExpensesService;

    public GenerateRecurringExpensesJob(GenerateRecurringExpensesService generateRecurringExpensesService) {
        this.generateRecurringExpensesService = generateRecurringExpensesService;
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Africa/Dar_es_Salaam")
    public void run() {
        generateRecurringExpensesService.generate();
    }
}
