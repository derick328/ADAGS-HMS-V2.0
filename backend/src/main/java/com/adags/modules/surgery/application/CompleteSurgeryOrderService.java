package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.AnaesthesiaNotRecordedException;
import com.adags.modules.surgery.domain.AnaesthesiaPaperRecordRepository;
import com.adags.modules.surgery.domain.ChecklistNotSatisfiedException;
import com.adags.modules.surgery.domain.ChecklistPhase;
import com.adags.modules.surgery.domain.InvalidSurgeryOrderTransitionException;
import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedureItemRepository;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
import com.adags.modules.surgery.domain.SurgeryReconciliationIncompleteException;
import com.adags.modules.surgery.domain.SurgicalChecklistRepository;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// Phase 8 item 3: "SurgeryOrder cannot reach COMPLETED without
// reconciliation balanced. COMPLETED cannot be reached without
// reconciliation. COMPLETED gates payment — send for payment only after."
//
// "Send for payment" (posting a charge for quantity_used per item) is
// deliberately not built here — item_id resolves through the inventory
// catalogue, not the billing service-item catalogue, and no mapping
// between the two exists yet. This service only enforces the completion
// gate itself; charge posting is a later item's concern, same boundary
// PrepareProcedureItemService already draws (item 2: "no charge is posted
// here at all").
@Service
public class CompleteSurgeryOrderService {

    private final SurgeryOrderRepository surgeryOrderRepository;
    private final ProcedureItemRepository procedureItemRepository;
    private final SurgicalChecklistRepository surgicalChecklistRepository;
    private final AnaesthesiaPaperRecordRepository anaesthesiaPaperRecordRepository;
    private final DomainEventPublisher eventPublisher;

    public CompleteSurgeryOrderService(
            SurgeryOrderRepository surgeryOrderRepository,
            ProcedureItemRepository procedureItemRepository,
            SurgicalChecklistRepository surgicalChecklistRepository,
            AnaesthesiaPaperRecordRepository anaesthesiaPaperRecordRepository,
            DomainEventPublisher eventPublisher
    ) {
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.procedureItemRepository = procedureItemRepository;
        this.surgicalChecklistRepository = surgicalChecklistRepository;
        this.anaesthesiaPaperRecordRepository = anaesthesiaPaperRecordRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SurgeryOrder complete(CompleteSurgeryOrderCommand command) {
        SurgeryOrder order = surgeryOrderRepository.findById(command.surgeryOrderId())
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + command.surgeryOrderId()));
        if (!order.status().canTransitionTo(SurgeryOrderStatus.COMPLETED)) {
            throw new InvalidSurgeryOrderTransitionException(order.id(), order.status(), SurgeryOrderStatus.COMPLETED);
        }

        // 04-*.md §7: "SIGN_OUT incomplete -> status cannot reach
        // COMPLETED" — and the swab/instrument count is never waivable,
        // so this checks count_correct = TRUE specifically, not just that
        // a SIGN_OUT row exists (SurgicalChecklist.satisfiesGate).
        if (!surgicalChecklistRepository.isPhaseSatisfied(order.id(), ChecklistPhase.SIGN_OUT)) {
            throw new ChecklistNotSatisfiedException(order.id(), ChecklistPhase.SIGN_OUT);
        }

        // 04-*.md §4: "A procedure requiring anaesthesia cannot reach
        // COMPLETED without either this pointer or an explicit
        // no_anaesthesia flag" — every SurgeryOrder carries a planned
        // anesthesiaType, so this check is unconditional.
        if (!anaesthesiaPaperRecordRepository.existsForSurgeryOrder(order.id())) {
            throw new AnaesthesiaNotRecordedException(order.id());
        }

        List<ProcedureItem> items = procedureItemRepository.findBySurgeryOrderId(order.id());
        boolean allReconciledAndAttested = items.stream().allMatch(item ->
                item.reconciledBy() != null && item.attestedBy() != null
                        && item.quantityUsed() + item.quantityReturned() + item.quantityWasted() == item.quantityPrepared());
        if (!allReconciledAndAttested) {
            throw new SurgeryReconciliationIncompleteException(order.id());
        }

        boolean transitioned = surgeryOrderRepository.transitionStatus(
                order.id(), SurgeryOrderStatus.IN_PROGRESS, SurgeryOrderStatus.COMPLETED);
        if (!transitioned) {
            throw new InvalidSurgeryOrderTransitionException(order.id(), order.status(), SurgeryOrderStatus.COMPLETED);
        }

        eventPublisher.publish("SurgeryCompleted", Map.of(
                "surgeryOrderId", order.id().toString(),
                "encounterId", order.encounterId().toString()));

        return surgeryOrderRepository.findById(order.id()).orElseThrow();
    }
}
