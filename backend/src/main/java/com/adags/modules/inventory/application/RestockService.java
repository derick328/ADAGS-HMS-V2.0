package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.MovementType;
import com.adags.modules.inventory.domain.StockBalanceRepository;
import com.adags.modules.inventory.domain.StockBatch;
import com.adags.modules.inventory.domain.StockBatchRepository;
import com.adags.modules.inventory.domain.StockMovement;
import com.adags.modules.inventory.domain.StockMovementRepository;
import com.adags.modules.inventory.domain.Store;
import com.adags.modules.inventory.domain.StoreRepository;
import com.adags.modules.inventory.domain.Supplier;
import com.adags.modules.inventory.domain.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.time.LocalDate;
import java.time.ZoneId;

// 12-pharmacist.md §7.2 (D3, D6): batch_number, expiry_date, unit_cost,
// supplier and delivery_note_ref are all mandatory — "the minimum control
// on stock creation." One restock is one StockBatch row, one
// StockMovement(RECEIPT) row and one materialised StockBalance update, all
// in the same transaction (root CLAUDE.md "Data").
@Service
public class RestockService {

    private final ItemRepository itemRepository;
    private final StoreRepository storeRepository;
    private final SupplierRepository supplierRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockBalanceRepository stockBalanceRepository;

    public RestockService(
            ItemRepository itemRepository,
            StoreRepository storeRepository,
            SupplierRepository supplierRepository,
            StockBatchRepository stockBatchRepository,
            StockMovementRepository stockMovementRepository,
            StockBalanceRepository stockBalanceRepository
    ) {
        this.itemRepository = itemRepository;
        this.storeRepository = storeRepository;
        this.supplierRepository = supplierRepository;
        this.stockBatchRepository = stockBatchRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.stockBalanceRepository = stockBalanceRepository;
    }

    @Transactional
    public StockBatch restock(RestockCommand command) {
        if (command.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be > 0.");
        }
        if (command.expiryDate() == null || !command.expiryDate().isAfter(
                LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam")))) {
            throw new IllegalArgumentException("Expiry date must be after today's facility-local date.");
        }

        Item item = itemRepository.findById(command.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + command.itemId()));
        // 14-*.md §3: CONTROLLED is blocked from restock. Checked here
        // (friendly error) and again by trg_block_controlled_item_stock_batch
        // (V24, the backstop for a path that skips this call).
        item.assertDispensable();
        Store store = storeRepository.findById(command.storeId())
                .orElseThrow(() -> new NoSuchElementException("Store not found: " + command.storeId()));
        Supplier supplier = supplierRepository.findById(command.supplierId())
                .orElseThrow(() -> new NoSuchElementException("Supplier not found: " + command.supplierId()));

        StockBatch batch = StockBatch.receive(item.id(), store.id(), command.batchNumber(), command.expiryDate(),
                command.unitCost(), supplier.id(), command.deliveryNoteRef(), command.actorUserId(),
                command.notes());
        StockBatch saved = stockBatchRepository.insert(batch);

        stockMovementRepository.insert(StockMovement.record(item.id(), saved.id(), store.id(), command.quantity(),
                MovementType.RECEIPT, "STOCK_BATCH", saved.id(), command.actorUserId()));

        stockBalanceRepository.applyDelta(item.id(), saved.id(), store.id(), command.quantity());

        return saved;
    }
}
