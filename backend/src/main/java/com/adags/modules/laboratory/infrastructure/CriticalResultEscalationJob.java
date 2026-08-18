package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.application.CriticalResultEscalationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 15-lab-technician.md §7: "unacknowledged after N minutes -> escalate to
// CLINICAL_LEAD." Same convention as AuditLogPartitionMaintenanceJob — the
// annotated method is a thin wrapper around a directly callable service
// method, so a test can run one sweep deterministically instead of waiting
// on real wall-clock time.
@Component
public class CriticalResultEscalationJob {

    private final CriticalResultEscalationService criticalResultEscalationService;

    public CriticalResultEscalationJob(CriticalResultEscalationService criticalResultEscalationService) {
        this.criticalResultEscalationService = criticalResultEscalationService;
    }

    @Scheduled(fixedRate = 60_000)
    public void sweep() {
        criticalResultEscalationService.escalateOverdue();
    }
}
