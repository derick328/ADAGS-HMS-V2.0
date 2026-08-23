package com.adags.modules.surgery.application;

import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedureItemRepository;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderNotOpenException;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

// Phase 8 item 2: "consumption is discovered, not ordered" — this is the
// physical-pull moment, not a charge. Invariants applying here (root
// CLAUDE.md):
//  - Data: nothing here is an order to fulfil later; the stock decrement
//    happens now, at prepare time, for the full quantityPrepared —
//    whatever is not used comes back as a return (item 3), it is never
//    "un-decremented" by simply not charging for it.
//  - Billing: "Charges built from quantity_used, not quantity_prepared" —
//    no charge is posted here at all; this service only moves stock.
//  - Safety: CONTROLLED items are blocked "in the repository layer so new
//    endpoints inherit it" — InventoryFacade.decrementForProcedureItem
//    calls the same Item.assertDispensable() every other consuming path
//    calls, so this endpoint inherits the block automatically.
@Service
public class PrepareProcedureItemService {

    private static final Set<SurgeryOrderStatus> OPEN_STATUSES =
            Set.of(SurgeryOrderStatus.SCHEDULED, SurgeryOrderStatus.IN_PROGRESS);

    private final ProcedureItemRepository procedureItemRepository;
    private final SurgeryOrderRepository surgeryOrderRepository;
    private final InventoryFacade inventoryFacade;

    public PrepareProcedureItemService(
            ProcedureItemRepository procedureItemRepository,
            SurgeryOrderRepository surgeryOrderRepository,
            InventoryFacade inventoryFacade
    ) {
        this.procedureItemRepository = procedureItemRepository;
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.inventoryFacade = inventoryFacade;
    }

    @Transactional
    public ProcedureItem prepare(PrepareProcedureItemCommand command) {
        SurgeryOrder order = surgeryOrderRepository.findById(command.surgeryOrderId())
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + command.surgeryOrderId()));
        if (!OPEN_STATUSES.contains(order.status())) {
            throw new SurgeryOrderNotOpenException(order.id(), order.status());
        }

        // Pre-generated id (same pattern as SurgeryOrder/TriageAssessment):
        // the stock movement needs to reference this row before it exists.
        UUID procedureItemId = UUID.randomUUID();
        inventoryFacade.decrementForProcedureItem(
                command.itemId(), command.batchId(), command.quantityPrepared(), procedureItemId, command.preparedBy());

        ProcedureItem toInsert = ProcedureItem.prepare(
                command.surgeryOrderId(), command.itemId(), command.phase(), command.quantityPrepared(),
                command.batchId(), command.preparedBy()).withId(procedureItemId);
        return procedureItemRepository.insert(toInsert);
    }
}
