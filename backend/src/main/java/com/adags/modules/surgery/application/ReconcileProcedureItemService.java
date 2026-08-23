package com.adags.modules.surgery.application;

import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.surgery.domain.MissingWastageReasonException;
import com.adags.modules.surgery.domain.ProcedureItem;
import com.adags.modules.surgery.domain.ProcedureItemAlreadyReconciledException;
import com.adags.modules.surgery.domain.ProcedureItemNotBalancedException;
import com.adags.modules.surgery.domain.ProcedureItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 8 item 3: "Reconciliation gates completion" — the circulating
// nurse's half. Invariants applying here (root CLAUDE.md / task text):
//  - 04-procedure-bedday-rules.md §2.3: "quantity_prepared ==
//    quantity_used + quantity_returned + quantity_wasted" and "wastage
//    requires a reason" — both checked before anything is written.
//  - "Returned items write a positive StockMovement back to the source
//    batch, or they are lost from the ledger" — InventoryFacade.
//    incrementForReturn, against the same batch_id this item was prepared
//    from, inside the same transaction as the reconcile write.
//  - Data: nothing here posts a charge. "Charges built from quantity_used"
//    (item 2) is a later item's concern — this only finalises the count.
@Service
public class ReconcileProcedureItemService {

    private final ProcedureItemRepository procedureItemRepository;
    private final InventoryFacade inventoryFacade;

    public ReconcileProcedureItemService(ProcedureItemRepository procedureItemRepository, InventoryFacade inventoryFacade) {
        this.procedureItemRepository = procedureItemRepository;
        this.inventoryFacade = inventoryFacade;
    }

    @Transactional
    public ProcedureItem reconcile(ReconcileProcedureItemCommand command) {
        ProcedureItem item = procedureItemRepository.findById(command.procedureItemId())
                .orElseThrow(() -> new NoSuchElementException("Procedure item not found: " + command.procedureItemId()));
        if (item.reconciledBy() != null) {
            throw new ProcedureItemAlreadyReconciledException(item.id());
        }

        int accountedFor = command.quantityUsed() + command.quantityReturned() + command.quantityWasted();
        if (accountedFor != item.quantityPrepared()) {
            throw new ProcedureItemNotBalancedException(item.id(), item.quantityPrepared(),
                    command.quantityUsed(), command.quantityReturned(), command.quantityWasted());
        }
        if (command.quantityWasted() > 0 && (command.wastageReason() == null || command.wastageReason().isBlank())) {
            throw new MissingWastageReasonException(item.id());
        }

        if (command.quantityReturned() > 0) {
            inventoryFacade.incrementForReturn(
                    item.itemId(), item.batchId(), command.quantityReturned(), item.id(), command.reconciledBy());
        }

        boolean updated = procedureItemRepository.reconcile(item.id(), command.quantityUsed(),
                command.quantityReturned(), command.quantityWasted(), command.wastageReason(), command.reconciledBy());
        if (!updated) {
            // Lost the race against a concurrent reconcile — the return
            // stock movement above already posted under this actor, same
            // as any other lost-race caller; the repository's WHERE guard
            // is what actually prevented the double write to the row.
            throw new ProcedureItemAlreadyReconciledException(item.id());
        }

        return procedureItemRepository.findById(item.id()).orElseThrow();
    }
}
