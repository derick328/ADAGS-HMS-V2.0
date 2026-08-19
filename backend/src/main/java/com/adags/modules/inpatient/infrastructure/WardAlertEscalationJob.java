package com.adags.modules.inpatient.infrastructure;

import com.adags.modules.inpatient.application.WardAlertEscalationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 17-ward-nurse.md §4.3, mirroring laboratory.CriticalResultEscalationJob.
@Component
public class WardAlertEscalationJob {

    private final WardAlertEscalationService wardAlertEscalationService;

    public WardAlertEscalationJob(WardAlertEscalationService wardAlertEscalationService) {
        this.wardAlertEscalationService = wardAlertEscalationService;
    }

    @Scheduled(fixedRate = 60_000)
    public void sweep() {
        wardAlertEscalationService.escalateOverdue();
    }
}
