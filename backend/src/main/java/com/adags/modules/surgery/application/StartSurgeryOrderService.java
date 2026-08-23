package com.adags.modules.surgery.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.surgery.domain.ChecklistNotSatisfiedException;
import com.adags.modules.surgery.domain.ChecklistPhase;
import com.adags.modules.surgery.domain.ConsentNotObtainedException;
import com.adags.modules.surgery.domain.InvalidSurgeryOrderTransitionException;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
import com.adags.modules.surgery.domain.SurgicalChecklistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 8 item 4: SCHEDULED -> IN_PROGRESS — the transition item 1 left
// unbuilt ("no consuming endpoint calls the billing gate yet"). Three
// gates, all real, all checked live at this exact moment:
//  - 04-*.md §7: "TIME_OUT incomplete -> status cannot move to IN_PROGRESS."
//  - task text (item 1): "Check again at procedure start — consent may be
//    revoked." Re-read from the row, not trusted from scheduling time.
//  - root CLAUDE.md "Billing": "Gate positions: ... procedure start."
//    IMMEDIATE orders already hold a PaymentBypass from scheduling
//    (ScheduleSurgeryService) so this call is a no-op for them — same
//    "durable once authorized" behaviour every other gate call has.
@Service
public class StartSurgeryOrderService {

    private final SurgeryOrderRepository surgeryOrderRepository;
    private final SurgicalChecklistRepository surgicalChecklistRepository;
    private final BillingFacade billingFacade;

    public StartSurgeryOrderService(
            SurgeryOrderRepository surgeryOrderRepository,
            SurgicalChecklistRepository surgicalChecklistRepository,
            BillingFacade billingFacade
    ) {
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.surgicalChecklistRepository = surgicalChecklistRepository;
        this.billingFacade = billingFacade;
    }

    @Transactional
    public SurgeryOrder start(StartSurgeryOrderCommand command) {
        SurgeryOrder order = surgeryOrderRepository.findById(command.surgeryOrderId())
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + command.surgeryOrderId()));
        if (!order.status().canTransitionTo(SurgeryOrderStatus.IN_PROGRESS)) {
            throw new InvalidSurgeryOrderTransitionException(order.id(), order.status(), SurgeryOrderStatus.IN_PROGRESS);
        }
        if (!order.consentObtained()) {
            throw new ConsentNotObtainedException(order.encounterId());
        }
        if (!surgicalChecklistRepository.isPhaseSatisfied(order.id(), ChecklistPhase.TIME_OUT)) {
            throw new ChecklistNotSatisfiedException(order.id(), ChecklistPhase.TIME_OUT);
        }

        // root CLAUDE.md "Billing": the procedure-start gate. Throws
        // BillingGateRejectedException (402) if unsatisfied — nothing
        // above this line is undone, since nothing above wrote anything.
        billingFacade.authorizeConsumption(order.invoiceLineId(), command.actorUserId());

        boolean transitioned = surgeryOrderRepository.transitionStatus(
                order.id(), SurgeryOrderStatus.SCHEDULED, SurgeryOrderStatus.IN_PROGRESS);
        if (!transitioned) {
            throw new InvalidSurgeryOrderTransitionException(order.id(), order.status(), SurgeryOrderStatus.IN_PROGRESS);
        }

        return surgeryOrderRepository.findById(order.id()).orElseThrow();
    }
}
