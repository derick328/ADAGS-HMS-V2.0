package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.Admission;
import com.adags.modules.inpatient.domain.AdmissionRepository;
import com.adags.modules.inpatient.domain.StaffDirectory;
import com.adags.modules.inpatient.domain.WardAlertNotification;
import com.adags.modules.inpatient.domain.WardAlertNotificationRepository;
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

// 17-ward-nurse.md §4.3: "unacknowledged after N minutes -> escalate to
// CLINICAL_LEAD" — mirrors laboratory.CriticalResultEscalationService
// exactly, including exposing escalateOverdue() directly-callable so a
// test can trigger one sweep deterministically.
@Service
public class WardAlertEscalationService {

    private static final String ESCALATION_WINDOW_SETTING = "ward.alert.escalation_window_minutes";
    private static final String WARD_ALERT_ESCALATED_EVENT = "WardAlertEscalated";

    private final WardAlertNotificationRepository wardAlertNotificationRepository;
    private final AdmissionRepository admissionRepository;
    private final StaffDirectory staffDirectory;
    private final ProtocolSettingReader protocolSettingReader;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public WardAlertEscalationService(
            WardAlertNotificationRepository wardAlertNotificationRepository,
            AdmissionRepository admissionRepository,
            StaffDirectory staffDirectory,
            ProtocolSettingReader protocolSettingReader,
            DomainEventPublisher eventPublisher,
            Clock clock
    ) {
        this.wardAlertNotificationRepository = wardAlertNotificationRepository;
        this.admissionRepository = admissionRepository;
        this.staffDirectory = staffDirectory;
        this.protocolSettingReader = protocolSettingReader;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public int escalateOverdue() {
        double windowMinutes = protocolSettingReader.getDouble(ESCALATION_WINDOW_SETTING);
        Instant cutoff = Instant.now(clock).minus(Duration.ofMillis((long) (windowMinutes * 60_000)));

        List<WardAlertNotification> overdue = wardAlertNotificationRepository.findOverdueUnescalated(cutoff);
        int escalatedCount = 0;
        for (WardAlertNotification notification : overdue) {
            if (escalateOne(notification)) {
                escalatedCount++;
            }
        }
        return escalatedCount;
    }

    private boolean escalateOne(WardAlertNotification notification) {
        Admission admission = admissionRepository.findById(notification.admissionId())
                .orElseThrow(() -> new NoSuchElementException("Admission not found: " + notification.admissionId()));

        // No CLINICAL_LEAD configured for this facility yet: leave
        // unescalated for the next sweep, same treatment the lab
        // escalation service gives a missing clinical lead.
        return staffDirectory.findOneActiveClinicalLead(admission.facilityId())
                .map(clinicalLeadId -> {
                    boolean escalated = wardAlertNotificationRepository.escalate(notification.id(), clinicalLeadId);
                    if (escalated) {
                        eventPublisher.publish(WARD_ALERT_ESCALATED_EVENT, Map.of(
                                "notificationId", notification.id().toString(),
                                "admissionId", notification.admissionId().toString(),
                                "escalatedToUserId", clinicalLeadId.toString()));
                    }
                    return escalated;
                })
                .orElse(false);
    }
}
