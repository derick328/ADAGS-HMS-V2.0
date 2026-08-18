package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.CriticalNotificationAlreadyAcknowledgedException;
import com.adags.modules.laboratory.domain.CriticalResultNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 15-lab-technician.md §7: "acknowledgement required and tracked ... tech's
// screen shows acknowledgement status so they know it landed." Acknowledging
// after escalation is still accepted — the doctor showing up late is a
// resolved incident either way, not an error.
@Service
public class AcknowledgeCriticalResultService {

    private final CriticalResultNotificationRepository criticalResultNotificationRepository;

    public AcknowledgeCriticalResultService(CriticalResultNotificationRepository criticalResultNotificationRepository) {
        this.criticalResultNotificationRepository = criticalResultNotificationRepository;
    }

    @Transactional
    public void acknowledge(UUID notificationId, UUID acknowledgedBy) {
        boolean updated = criticalResultNotificationRepository.acknowledge(notificationId, acknowledgedBy);
        if (!updated) {
            throw new CriticalNotificationAlreadyAcknowledgedException(notificationId);
        }
    }
}
