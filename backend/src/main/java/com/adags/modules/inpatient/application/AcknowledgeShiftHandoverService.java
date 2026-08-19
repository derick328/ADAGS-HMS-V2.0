package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.ShiftHandoverAlreadyAcknowledgedException;
import com.adags.modules.inpatient.domain.ShiftHandoverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 17-ward-nurse.md §7: "Outgoing nurse records; incoming nurse
// acknowledges. An unacknowledged handover is flagged."
@Service
public class AcknowledgeShiftHandoverService {

    private final ShiftHandoverRepository shiftHandoverRepository;

    public AcknowledgeShiftHandoverService(ShiftHandoverRepository shiftHandoverRepository) {
        this.shiftHandoverRepository = shiftHandoverRepository;
    }

    @Transactional
    public void acknowledge(UUID handoverId, UUID acknowledgedBy) {
        boolean updated = shiftHandoverRepository.acknowledge(handoverId, acknowledgedBy);
        if (!updated) {
            throw new ShiftHandoverAlreadyAcknowledgedException(handoverId);
        }
    }
}
