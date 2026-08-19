package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.application.ReconcileOutstandingBalancesService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 02-billing-architecture.md §9.3: "a nightly job recomputing from source
// rows and alerting on drift." Facility-local off-peak hour, same
// reasoning as backend/CLAUDE.md "Time" (store UTC, act facility-local)
// and WardChargeAccrualJob's fixed cron zone.
@Component
public class OutstandingBalanceReconciliationJob {

    private final ReconcileOutstandingBalancesService reconcileOutstandingBalancesService;

    public OutstandingBalanceReconciliationJob(ReconcileOutstandingBalancesService reconcileOutstandingBalancesService) {
        this.reconcileOutstandingBalancesService = reconcileOutstandingBalancesService;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Africa/Dar_es_Salaam")
    public void run() {
        reconcileOutstandingBalancesService.reconcile();
    }
}
