package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.application.WardChargeAccrualSweepService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 04-procedure-bedday-rules.md §3.5: "Runs daily at 10:00 (facility local
// time), not midnight." The cron zone is fixed facility-local, same
// reasoning as backend/CLAUDE.md "Time": store UTC, act on facility-local.
// Catch-up is inherent to the sweep, not this scheduler — WardChargeAccrualService
// posts every boundary since an admission's last row, so a server outage
// spanning a missed 10:00 firing is caught up whole by the next one, and a
// duplicate firing (this job re-triggered manually while already run today)
// posts nothing new, since every boundary up to now is already covered.
@Component
public class WardChargeAccrualJob {

    private final WardChargeAccrualSweepService wardChargeAccrualSweepService;

    public WardChargeAccrualJob(WardChargeAccrualSweepService wardChargeAccrualSweepService) {
        this.wardChargeAccrualSweepService = wardChargeAccrualSweepService;
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Africa/Dar_es_Salaam")
    public void run() {
        wardChargeAccrualSweepService.sweep();
    }
}
