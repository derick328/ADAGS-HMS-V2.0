package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.WardAlertAlreadyAcknowledgedException;
import com.adags.modules.inpatient.domain.WardAlertNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 17-ward-nurse.md §4.3, mirroring laboratory's AcknowledgeCriticalResultService.
@Service
public class AcknowledgeWardAlertService {

    private final WardAlertNotificationRepository wardAlertNotificationRepository;

    public AcknowledgeWardAlertService(WardAlertNotificationRepository wardAlertNotificationRepository) {
        this.wardAlertNotificationRepository = wardAlertNotificationRepository;
    }

    @Transactional
    public void acknowledge(UUID notificationId, UUID acknowledgedBy) {
        boolean updated = wardAlertNotificationRepository.acknowledge(notificationId, acknowledgedBy);
        if (!updated) {
            throw new WardAlertAlreadyAcknowledgedException(notificationId);
        }
    }
}
