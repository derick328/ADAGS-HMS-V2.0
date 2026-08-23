package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.EmergencyReductionNotAllowedException;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryUrgency;
import com.adags.modules.surgery.domain.SurgicalChecklist;
import com.adags.modules.surgery.domain.SurgicalChecklistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 8 item 4: records one WHO-style checklist pause. Whether
// isEmergencyReduced is legitimate needs the order's actual urgency — the
// one check SurgicalChecklist.complete can't make itself (domain has no
// I/O) — so it happens here, before the structural checks in the factory.
@Service
public class CompleteChecklistService {

    private final SurgicalChecklistRepository surgicalChecklistRepository;
    private final SurgeryOrderRepository surgeryOrderRepository;

    public CompleteChecklistService(
            SurgicalChecklistRepository surgicalChecklistRepository, SurgeryOrderRepository surgeryOrderRepository
    ) {
        this.surgicalChecklistRepository = surgicalChecklistRepository;
        this.surgeryOrderRepository = surgeryOrderRepository;
    }

    @Transactional
    public SurgicalChecklist complete(CompleteChecklistCommand command) {
        SurgeryOrder order = surgeryOrderRepository.findById(command.surgeryOrderId())
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + command.surgeryOrderId()));

        if (command.isEmergencyReduced() && order.urgency() != SurgeryUrgency.IMMEDIATE) {
            throw new EmergencyReductionNotAllowedException(order.id(), "order urgency is not IMMEDIATE");
        }

        SurgicalChecklist toInsert = SurgicalChecklist.complete(order.id(), command.phase(), command.items(),
                command.countCorrect(), command.countDiscrepancyNote(), command.isEmergencyReduced(),
                command.completedBy());
        return surgicalChecklistRepository.insert(toInsert);
    }
}
