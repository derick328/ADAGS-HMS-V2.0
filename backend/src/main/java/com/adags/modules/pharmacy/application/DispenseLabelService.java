package com.adags.modules.pharmacy.application;

import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.patients.PatientFacade;
import com.adags.modules.pharmacy.domain.Dispense;
import com.adags.modules.pharmacy.domain.DispenseLine;
import com.adags.modules.pharmacy.domain.DispenseLineRepository;
import com.adags.modules.pharmacy.domain.DispenseRepository;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.shared.kernel.BatchAvailability;
import com.adags.shared.kernel.ItemSummary;
import com.adags.shared.kernel.PatientIdentity;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §4.6. Read-only, assembled from what's already
// persisted after a successful dispense — never invented data, and never
// touches the gate or the ledger.
@Service
public class DispenseLabelService {

    private static final ZoneId FACILITY_ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    private final DispenseRepository dispenseRepository;
    private final DispenseLineRepository dispenseLineRepository;
    private final PrescriptionLineRepository prescriptionLineRepository;
    private final PrescriptionOrderRepository prescriptionOrderRepository;
    private final InventoryFacade inventoryFacade;
    private final PatientFacade patientFacade;

    public DispenseLabelService(
            DispenseRepository dispenseRepository,
            DispenseLineRepository dispenseLineRepository,
            PrescriptionLineRepository prescriptionLineRepository,
            PrescriptionOrderRepository prescriptionOrderRepository,
            InventoryFacade inventoryFacade,
            PatientFacade patientFacade
    ) {
        this.dispenseRepository = dispenseRepository;
        this.dispenseLineRepository = dispenseLineRepository;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.inventoryFacade = inventoryFacade;
        this.patientFacade = patientFacade;
    }

    public DispenseLabel generate(UUID dispenseId) {
        Dispense dispense = dispenseRepository.findById(dispenseId)
                .orElseThrow(() -> new NoSuchElementException("Dispense not found: " + dispenseId));
        List<DispenseLine> lines = dispenseLineRepository.findByDispenseId(dispenseId);

        PrescriptionLine prescriptionLine = prescriptionLineRepository.findById(dispense.prescriptionLineId())
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + dispense.prescriptionLineId()));
        PrescriptionOrder order = prescriptionOrderRepository.findById(prescriptionLine.prescriptionOrderId())
                .orElseThrow(() -> new NoSuchElementException("Prescription order not found: " + prescriptionLine.prescriptionOrderId()));
        ItemSummary item = inventoryFacade.findItemSummary(prescriptionLine.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + prescriptionLine.itemId()));
        PatientIdentity patient = patientFacade.findIdentity(order.patientId())
                .orElseThrow(() -> new NoSuchElementException("Patient not found: " + order.patientId()));

        // One entry per distinct batch — a dispense line-per-batch model
        // could in principle repeat a batch across two lines (e.g. two
        // separate manual overrides against the same batch); the label
        // shows it once.
        Map<UUID, LabelBatchLine> batchLines = new LinkedHashMap<>();
        for (DispenseLine line : lines) {
            batchLines.computeIfAbsent(line.batchId(), id -> {
                BatchAvailability batch = inventoryFacade.findBatch(id)
                        .orElseThrow(() -> new NoSuchElementException("Batch not found: " + id));
                return new LabelBatchLine(batch.batchNumber(), batch.expiryDate());
            });
        }

        return new DispenseLabel(patient.fullName(), patient.hospitalNumber(), item.name(),
                prescriptionLine.dosageInstructions(), dispense.dispensedAt().atZone(FACILITY_ZONE).toLocalDate(),
                List.copyOf(batchLines.values()));
    }
}
