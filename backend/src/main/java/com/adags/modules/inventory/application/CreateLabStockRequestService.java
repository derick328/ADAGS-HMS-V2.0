package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.Item;
import com.adags.modules.inventory.domain.ItemRepository;
import com.adags.modules.inventory.domain.LabStockRequest;
import com.adags.modules.inventory.domain.LabStockRequestRepository;
import com.adags.modules.inventory.domain.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 12-pharmacist.md §7: LAB_STOCK_REQUEST_CREATE — a lab technician
// requesting reagents from the pharmacy store.
@Service
public class CreateLabStockRequestService {

    private final ItemRepository itemRepository;
    private final StoreRepository storeRepository;
    private final LabStockRequestRepository labStockRequestRepository;

    public CreateLabStockRequestService(
            ItemRepository itemRepository, StoreRepository storeRepository, LabStockRequestRepository labStockRequestRepository
    ) {
        this.itemRepository = itemRepository;
        this.storeRepository = storeRepository;
        this.labStockRequestRepository = labStockRequestRepository;
    }

    @Transactional
    public LabStockRequest create(CreateLabStockRequestCommand command) {
        Item item = itemRepository.findById(command.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + command.itemId()));
        storeRepository.findById(command.requestingStoreId())
                .orElseThrow(() -> new NoSuchElementException("Store not found: " + command.requestingStoreId()));

        LabStockRequest request = LabStockRequest.request(
                item.id(), command.requestedQuantity(), command.requestingStoreId(), command.requestedBy());
        return labStockRequestRepository.insert(request);
    }
}
