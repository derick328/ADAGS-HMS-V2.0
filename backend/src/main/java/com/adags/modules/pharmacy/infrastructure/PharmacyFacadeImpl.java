package com.adags.modules.pharmacy.infrastructure;

import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.pharmacy.PharmacyFacade;
import com.adags.modules.pharmacy.domain.Dispense;
import com.adags.modules.pharmacy.domain.DispenseLine;
import com.adags.modules.pharmacy.domain.DispenseLineRepository;
import com.adags.modules.pharmacy.domain.DispenseRepository;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.shared.kernel.DispensedBatchSummary;
import com.adags.shared.kernel.ItemSummary;
import com.adags.shared.kernel.PrescriptionLineMarSummary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class PharmacyFacadeImpl implements PharmacyFacade {

    private final PrescriptionLineRepository prescriptionLineRepository;
    private final PrescriptionOrderRepository prescriptionOrderRepository;
    private final DispenseRepository dispenseRepository;
    private final DispenseLineRepository dispenseLineRepository;
    private final InventoryFacade inventoryFacade;

    public PharmacyFacadeImpl(
            PrescriptionLineRepository prescriptionLineRepository,
            PrescriptionOrderRepository prescriptionOrderRepository,
            DispenseRepository dispenseRepository,
            DispenseLineRepository dispenseLineRepository,
            InventoryFacade inventoryFacade
    ) {
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.dispenseRepository = dispenseRepository;
        this.dispenseLineRepository = dispenseLineRepository;
        this.inventoryFacade = inventoryFacade;
    }

    @Override
    public Optional<PrescriptionLineMarSummary> findPrescriptionLineForMar(UUID prescriptionLineId) {
        Optional<PrescriptionLine> lineOpt = prescriptionLineRepository.findById(prescriptionLineId);
        if (lineOpt.isEmpty()) {
            return Optional.empty();
        }
        PrescriptionLine line = lineOpt.get();
        PrescriptionOrder order = prescriptionOrderRepository.findById(line.prescriptionOrderId())
                .orElseThrow(() -> new IllegalStateException("Prescription order not found: " + line.prescriptionOrderId()));
        ItemSummary item = inventoryFacade.findItemSummary(line.itemId())
                .orElseThrow(() -> new IllegalStateException("Item not found: " + line.itemId()));

        return Optional.of(new PrescriptionLineMarSummary(
                line.id(), line.itemId(), item.name(), line.route(), line.doseAmount(), line.doseUnit(),
                line.frequency() == null ? null : line.frequency().name(), line.durationDays(),
                order.prescribedAt(), order.prescribedBy(), item.requiresAdministrationWitness()));
    }

    @Override
    public List<DispensedBatchSummary> findDispensedBatches(UUID prescriptionLineId) {
        List<Dispense> dispenses = dispenseRepository.findByPrescriptionLineId(prescriptionLineId);
        Map<UUID, DispensedBatchSummary> byBatch = new HashMap<>();
        for (Dispense dispense : dispenses) {
            for (DispenseLine line : dispenseLineRepository.findByDispenseId(dispense.id())) {
                byBatch.merge(line.batchId(),
                        new DispensedBatchSummary(line.batchId(), batchNumber(line.batchId()), line.quantity()),
                        (existing, added) -> new DispensedBatchSummary(
                                existing.batchId(), existing.batchNumber(), existing.quantityDispensed() + added.quantityDispensed()));
            }
        }
        return List.copyOf(byBatch.values());
    }

    private String batchNumber(UUID batchId) {
        return inventoryFacade.findBatch(batchId).map(batch -> batch.batchNumber()).orElse(null);
    }
}
