package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.BatchExpiredException;
import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.LabStockRequest;
import com.adags.modules.inventory.domain.LabStockRequestRepository;
import com.adags.modules.inventory.domain.MovementType;
import com.adags.modules.inventory.domain.StockBalanceRepository;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.modules.inventory.domain.StockBatchStatus;
import com.adags.modules.inventory.domain.StockMovement;
import com.adags.modules.inventory.domain.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §7/§8: "A release is a transfer, not a dispense. Two
// movement rows — negative from the main pharmacy store, positive to the
// lab sub-store. A single decrement makes the reagent vanish from the
// ledger and leaves the lab's stock untracked." batch_id is identical on
// both movement rows and both resulting stock_balance rows — the transfer
// moves quantity between stores, never between batches, which is what
// keeps the Phase 4 reagent-lot traceability hook meaningful.
//
// 13-controlled-medicines-policy.md §3: transfer is one of the explicitly
// blocked actions for a CONTROLLED item, same as restock and dispensing.
// Expired/non-ACTIVE batches are hard-blocked the same way dispensing
// blocks them — a reagent that can't be dispensed to a patient shouldn't
// be transferable to the lab either.
@Service
public class ReleaseLabStockRequestService {

    private final LabStockRequestRepository labStockRequestRepository;
    private final ItemRepository itemRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockMovementRepository stockMovementRepository;

    public ReleaseLabStockRequestService(
            LabStockRequestRepository labStockRequestRepository,
            ItemRepository itemRepository,
            StockBatchRepository stockBatchRepository,
            StockBalanceRepository stockBalanceRepository,
            StockMovementRepository stockMovementRepository
    ) {
        this.labStockRequestRepository = labStockRequestRepository;
        this.itemRepository = itemRepository;
        this.stockBatchRepository = stockBatchRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public LabStockRequest release(ReleaseLabStockRequestCommand command) {
        LabStockRequest request = labStockRequestRepository.findById(command.requestId())
                .orElseThrow(() -> new NoSuchElementException("Lab stock request not found: " + command.requestId()));
        LabStockRequest updatedRequest = request.applyRelease(command.quantity(), command.reviewedBy());

        Item item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + request.itemId()));
        item.assertDispensable();

        StockBatch batch = stockBatchRepository.findById(command.batchId())
                .orElseThrow(() -> new NoSuchElementException("Batch not found: " + command.batchId()));
        if (batch.status() != StockBatchStatus.ACTIVE || batch.isExpiredAsOf(LocalDate.now())) {
            throw new BatchExpiredException(command.batchId());
        }

        UUID sourceStoreId = batch.storeId();
        UUID movementRef = UUID.randomUUID();

        stockBalanceRepository.applyDelta(item.id(), batch.id(), sourceStoreId, -command.quantity());
        stockMovementRepository.insert(StockMovement.record(item.id(), batch.id(), sourceStoreId, -command.quantity(),
                MovementType.TRANSFER_OUT, "LAB_STOCK_REQUEST", movementRef, command.reviewedBy()));

        stockBalanceRepository.applyDelta(item.id(), batch.id(), request.requestingStoreId(), command.quantity());
        stockMovementRepository.insert(StockMovement.record(item.id(), batch.id(), request.requestingStoreId(),
                command.quantity(), MovementType.TRANSFER_IN, "LAB_STOCK_REQUEST", movementRef, command.reviewedBy()));

        return labStockRequestRepository.update(updatedRequest);
    }
}
