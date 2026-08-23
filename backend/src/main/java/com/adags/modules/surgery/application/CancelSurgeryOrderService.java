package com.adags.modules.surgery.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.surgery.domain.InvalidSurgeryOrderTransitionException;
import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedureItemRepository;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 8 item 6: cancellation. 04-*.md §6's four billing branches all end
// the same way for the procedure fee — "voided or refunded" — which is
// exactly what BillingFacade.voidUnpaidServiceCharge already does (void if
// nothing settled yet, otherwise a no-op the caller routes to an
// accountant-led refund — see its own javadoc). No new billing machinery
// needed for the fee.
//
// For items already prepared: no per-item charge-posting mechanism exists
// yet (item 2/3's own drawn boundary), so "items charged" vs "items
// waived" can't be differentiated in money terms here. What IS concrete
// and buildable is the task's own ⚠: "Items waived still leave the stock
// ledger — quantity_wasted with the cancellation reason. The stock is gone
// either way; only the charge differs." Every unreconciled ProcedureItem
// is swept to quantity_wasted = quantity_prepared, wastage_reason = the
// cancellation reason, reusing ReconcileProcedureItemService — no return
// stock movement posts (quantityReturned = 0), matching "the stock is gone
// either way."
@Service
public class CancelSurgeryOrderService {

    private final SurgeryOrderRepository surgeryOrderRepository;
    private final ProcedureItemRepository procedureItemRepository;
    private final ReconcileProcedureItemService reconcileProcedureItemService;
    private final BillingFacade billingFacade;

    public CancelSurgeryOrderService(
            SurgeryOrderRepository surgeryOrderRepository,
            ProcedureItemRepository procedureItemRepository,
            ReconcileProcedureItemService reconcileProcedureItemService,
            BillingFacade billingFacade
    ) {
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.procedureItemRepository = procedureItemRepository;
        this.reconcileProcedureItemService = reconcileProcedureItemService;
        this.billingFacade = billingFacade;
    }

    @Transactional
    public SurgeryOrder cancel(CancelSurgeryOrderCommand command) {
        SurgeryOrder order = surgeryOrderRepository.findById(command.surgeryOrderId())
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + command.surgeryOrderId()));
        if (!order.status().canTransitionTo(SurgeryOrderStatus.CANCELLED)) {
            throw new InvalidSurgeryOrderTransitionException(order.id(), order.status(), SurgeryOrderStatus.CANCELLED);
        }

        billingFacade.voidUnpaidServiceCharge(
                order.invoiceLineId(), "Surgery cancelled: " + command.reason(), command.actorUserId());

        for (ProcedureItem item : procedureItemRepository.findBySurgeryOrderId(order.id())) {
            if (item.reconciledBy() == null) {
                reconcileProcedureItemService.reconcile(new ReconcileProcedureItemCommand(item.id(), 0, 0,
                        item.quantityPrepared(), "Surgery cancelled: " + command.reason(), command.actorUserId()));
            }
        }

        boolean cancelled = surgeryOrderRepository.recordCancellation(order.id(), command.reason(), command.actorUserId());
        if (!cancelled) {
            throw new InvalidSurgeryOrderTransitionException(order.id(), order.status(), SurgeryOrderStatus.CANCELLED);
        }

        return surgeryOrderRepository.findById(order.id()).orElseThrow();
    }
}
