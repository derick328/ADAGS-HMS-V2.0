package com.adags.modules.pharmacy.application;

import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.pharmacy.domain.BatchAllocation;
import com.adags.modules.pharmacy.domain.FefoAllocator;
import com.adags.modules.pharmacy.domain.InsufficientBatchStockException;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.shared.kernel.AllergySummary;
import com.adags.shared.kernel.BatchAvailability;
import com.adags.shared.kernel.ItemSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §5.2. Read-only — no gate check here, the gate is only
// ever evaluated at the actual dispense (build brief item 1: "queue
// membership is not the control"), so this view must never be mistaken
// for one.
@Service
public class DispenseContextService {

    private final PrescriptionLineRepository prescriptionLineRepository;
    private final PrescriptionOrderRepository prescriptionOrderRepository;
    private final InventoryFacade inventoryFacade;
    private final ClinicalFacade clinicalFacade;

    public DispenseContextService(
            PrescriptionLineRepository prescriptionLineRepository,
            PrescriptionOrderRepository prescriptionOrderRepository,
            InventoryFacade inventoryFacade,
            ClinicalFacade clinicalFacade
    ) {
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.inventoryFacade = inventoryFacade;
        this.clinicalFacade = clinicalFacade;
    }

    public DispenseContext build(UUID prescriptionLineId) {
        PrescriptionLine line = prescriptionLineRepository.findById(prescriptionLineId)
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + prescriptionLineId));
        PrescriptionOrder order = prescriptionOrderRepository.findById(line.prescriptionOrderId())
                .orElseThrow(() -> new NoSuchElementException("Prescription order not found: " + line.prescriptionOrderId()));

        ItemSummary item = inventoryFacade.findItemSummary(line.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + line.itemId()));

        // root CLAUDE.md "Safety": every allergy record, every
        // confirmation status — never filtered, never collapsed.
        List<AllergySummary> allergies = clinicalFacade.findAllergies(order.patientId());

        List<BatchAvailability> availableBatches = inventoryFacade.findFefoBatches(line.itemId());

        List<BatchAllocation> fefoSuggestion = List.of();
        if (line.remaining() > 0) {
            try {
                fefoSuggestion = FefoAllocator.allocate(line.itemId(), availableBatches, line.remaining());
            } catch (InsufficientBatchStockException notEnoughStock) {
                // Best-effort display — the pharmacist still needs to see
                // the patient's allergies and what stock does exist even
                // when it can't fully cover the remaining quantity; the
                // actual dispense attempt is what enforces sufficiency.
                fefoSuggestion = List.of();
            }
        }

        return new DispenseContext(line, item, allergies, availableBatches, fefoSuggestion);
    }
}
