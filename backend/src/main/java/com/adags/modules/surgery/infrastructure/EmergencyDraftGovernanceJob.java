package com.adags.modules.surgery.infrastructure;

import com.adags.modules.surgery.application.EmergencyDraftGovernanceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Phase 8 item 8: "48h -> CLINICAL_LEAD governance queue." Hourly is
// plenty of precision against a 48-hour threshold — same thin-wrapper
// convention as CriticalResultEscalationJob, the annotated method calling
// a directly-testable service method rather than embedding the sweep
// logic here.
@Component
public class EmergencyDraftGovernanceJob {

    private final EmergencyDraftGovernanceService emergencyDraftGovernanceService;

    public EmergencyDraftGovernanceJob(EmergencyDraftGovernanceService emergencyDraftGovernanceService) {
        this.emergencyDraftGovernanceService = emergencyDraftGovernanceService;
    }

    @Scheduled(fixedRate = 3_600_000)
    public void sweep() {
        emergencyDraftGovernanceService.escalateStaleDrafts();
    }
}
