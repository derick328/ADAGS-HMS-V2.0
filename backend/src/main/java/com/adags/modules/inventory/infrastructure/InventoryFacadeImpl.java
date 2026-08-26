package com.adags.modules.inventory.infrastructure;

import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.inventory.domain.BatchExpiredException;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.MovementType;
import com.adags.modules.inventory.domain.StockBalanceRepository;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.modules.inventory.domain.StockBatchStatus;
import com.adags.modules.inventory.domain.StockMovement;
import com.adags.modules.inventory.domain.StockMovementRepository;
import com.adags.shared.kernel.BatchAvailability;
import com.adags.shared.kernel.ItemSummary;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Component
public class InventoryFacadeImpl implements InventoryFacade {

    private final ItemRepository itemRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockMovementRepository stockMovementRepository;

    public InventoryFacadeImpl(
            ItemRepository itemRepository,
            StockBatchRepository stockBatchRepository,
            StockBalanceRepository stockBalanceRepository,
            StockMovementRepository stockMovementRepository
    ) {
        this.itemRepository = itemRepository;
        this.stockBatchRepository = stockBatchRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Override
    public boolean itemExists(UUID itemId) {
        return itemRepository.findById(itemId).isPresent();
    }

    @Override
    public boolean isDispensable(UUID itemId) {
        return itemRepository.findById(itemId)
                .map(item -> item.active() && item.regulatorySchedule().isDispensable())
                .orElse(false);
    }

    @Override
    public Optional<ItemSummary> findItemSummary(UUID itemId) {
        return itemRepository.findById(itemId)
                .map(item -> new ItemSummary(item.id(), item.code(), item.name(), item.unit(),
                        item.classification(), item.form(),
                        item.regulatorySchedule().isDispensable(), item.active(),
                        item.requiresAdministrationWitness()));
    }

    @Override
    public List<BatchAvailability> findFefoBatches(UUID itemId) {
        return stockBatchRepository.findActiveByItemFefo(itemId).stream()
                .map(batch -> new BatchAvailability(batch.id(), batch.itemId(), batch.batchNumber(),
                        batch.expiryDate(), stockBalanceRepository.quantityOnHand(batch.id())))
                .toList();
    }

    @Override
    public Optional<BatchAvailability> findBatch(UUID batchId) {
        return stockBatchRepository.findById(batchId)
                .map(batch -> new BatchAvailability(batch.id(), batch.itemId(), batch.batchNumber(),
                        batch.expiryDate(), stockBalanceRepository.quantityOnHand(batch.id())));
    }

    @Override
    @Transactional
    public Money decrementForDispense(UUID itemId, UUID batchId, int quantity, UUID referenceId, UUID actorUserId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        // Evaluated live, same discipline as the billing gate — the item's
        // regulatory_schedule could have changed to CONTROLLED since it
        // was prescribed.
        item.assertDispensable();

        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));
        if (batch.status() != StockBatchStatus.ACTIVE || batch.isExpiredAsOf(LocalDate.now())) {
            // 12-pharmacist.md §6.3: hard-blocked, not flagged — checked
            // against the calendar every time, not only the stored status
            // column, since no scheduled job flips it the instant a batch
            // expires (§7.3 is future work).
            throw new BatchExpiredException(batchId);
        }

        stockBalanceRepository.applyDelta(itemId, batchId, batch.storeId(), -quantity);

        stockMovementRepository.insert(StockMovement.record(itemId, batchId, batch.storeId(), -quantity,
                MovementType.DISPENSE, "DISPENSE_LINE", referenceId, actorUserId));

        return batch.unitCost();
    }

    @Override
    @Transactional
    public Money decrementForProcedureItem(UUID itemId, UUID batchId, int quantity, UUID referenceId, UUID actorUserId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        item.assertDispensable();

        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));
        if (batch.status() != StockBatchStatus.ACTIVE || batch.isExpiredAsOf(LocalDate.now())) {
            throw new BatchExpiredException(batchId);
        }

        stockBalanceRepository.applyDelta(itemId, batchId, batch.storeId(), -quantity);

        stockMovementRepository.insert(StockMovement.record(itemId, batchId, batch.storeId(), -quantity,
                MovementType.DISPENSE, "PROCEDURE_ITEM", referenceId, actorUserId));

        return batch.unitCost();
    }

    @Override
    @Transactional
    public void incrementForReturn(UUID itemId, UUID batchId, int quantity, UUID referenceId, UUID actorUserId) {
        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + batchId));

        stockBalanceRepository.applyDelta(itemId, batchId, batch.storeId(), quantity);

        stockMovementRepository.insert(StockMovement.record(itemId, batchId, batch.storeId(), quantity,
                MovementType.RETURN, "PROCEDURE_ITEM_RETURN", referenceId, actorUserId));
    }
}
