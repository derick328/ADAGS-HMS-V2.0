package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.ClinicalLeadDirectory;
import com.adags.modules.laboratory.domain.CriticalResultNotification;
import com.adags.modules.laboratory.domain.CriticalResultNotificationRepository;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.settings.ProtocolSettingReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// 15-lab-technician.md §7: "unacknowledged after N minutes -> escalate to
// CLINICAL_LEAD." Called by CriticalResultEscalationJob's @Scheduled sweep
// in production; exposed as a directly callable method (not private to the
// job) specifically so a test can invoke one sweep deterministically
// instead of waiting on real wall-clock time.
@Service
public class CriticalResultEscalationService {

    private static final String ESCALATION_WINDOW_SETTING = "lab.critical_result.escalation_window_minutes";
    private static final String CRITICAL_RESULT_ESCALATED_EVENT = "CriticalResultEscalated";

    private final CriticalResultNotificationRepository criticalResultNotificationRepository;
    private final LabResultRepository labResultRepository;
    private final LabOrderRepository labOrderRepository;
    private final ClinicalLeadDirectory clinicalLeadDirectory;
    private final ProtocolSettingReader protocolSettingReader;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public CriticalResultEscalationService(
            CriticalResultNotificationRepository criticalResultNotificationRepository,
            LabResultRepository labResultRepository,
            LabOrderRepository labOrderRepository,
            ClinicalLeadDirectory clinicalLeadDirectory,
            ProtocolSettingReader protocolSettingReader,
            DomainEventPublisher eventPublisher,
            Clock clock
    ) {
        this.criticalResultNotificationRepository = criticalResultNotificationRepository;
        this.labResultRepository = labResultRepository;
        this.labOrderRepository = labOrderRepository;
        this.clinicalLeadDirectory = clinicalLeadDirectory;
        this.protocolSettingReader = protocolSettingReader;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public int escalateOverdue() {
        double windowMinutes = protocolSettingReader.getDouble(ESCALATION_WINDOW_SETTING);
        Instant cutoff = Instant.now(clock).minus(Duration.ofMillis((long) (windowMinutes * 60_000)));

        List<CriticalResultNotification> overdue = criticalResultNotificationRepository.findOverdueUnescalated(cutoff);
        int escalatedCount = 0;
        for (CriticalResultNotification notification : overdue) {
            if (escalateOne(notification)) {
                escalatedCount++;
            }
        }
        return escalatedCount;
    }

    private boolean escalateOne(CriticalResultNotification notification) {
        LabResult result = labResultRepository.findById(notification.resultId())
                .orElseThrow(() -> new NoSuchElementException("Lab result not found: " + notification.resultId()));
        LabOrder order = labOrderRepository.findById(result.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + result.labOrderId()));

        // No CLINICAL_LEAD configured for this facility yet: leave
        // unescalated for the next sweep rather than silently marking it
        // escalated-to-nobody, which would look like it worked.
        return clinicalLeadDirectory.findOneActiveClinicalLead(order.facilityId())
                .map(clinicalLeadId -> {
                    boolean escalated = criticalResultNotificationRepository.escalate(notification.id(), clinicalLeadId);
                    if (escalated) {
                        eventPublisher.publish(CRITICAL_RESULT_ESCALATED_EVENT, Map.of(
                                "notificationId", notification.id().toString(),
                                "resultId", notification.resultId().toString(),
                                "escalatedToUserId", clinicalLeadId.toString()));
                    }
                    return escalated;
                })
                .orElse(false);
    }
}
