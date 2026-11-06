package com.adags.modules.pharmacy.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.pharmacy.domain.BatchAllocation;
import com.adags.modules.pharmacy.domain.Dispense;
import com.adags.modules.pharmacy.domain.DispenseLine;
import com.adags.modules.pharmacy.domain.DispenseLineRepository;
import com.adags.modules.pharmacy.domain.DispenseRepository;
import com.adags.modules.pharmacy.domain.FefoAllocator;
import com.adags.modules.pharmacy.domain.InsufficientBatchStockException;
import com.adags.modules.pharmacy.domain.ItemNotDispensableException;
import com.adags.modules.pharmacy.domain.MissingFefoOverrideReasonException;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.shared.kernel.BatchAvailability;
import com.adags.shared.kernel.Money;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §4, §5, §7 — the dispensing gate, repeatable batch
// lines, FEFO/override, expiry hard-block, concurrency and idempotency,
// and partial dispensing, all in one transaction:
//
//  1. Cheap, side-effect-free validations first (prescribed-quantity bound,
//     item still dispensable, FEFO deviation needs a reason) — a request
//     that's going to fail shouldn't touch the gate or the ledger first.
//  2. The billing gate, evaluated live (§5.1) — never cached from
//     prescribing time, since the patient likely paid since then.
//  3. The idempotency-keyed Dispense row, inserted with the same
//     check-first/insert/catch-the-race shape PostPaymentService uses —
//     a double-tap must not decrement stock twice or chart two dispenses.
//  4. Per batch line: InventoryFacade.decrementForDispense (the CHECK
//     (quantity >= 0) guard from build brief item 1, and the expiry hard-
//     block from item 2/3, both live inside this same transaction).
//  5. PrescriptionLine.quantity_dispensed/status materialised last.
@Service
public class DispenseService {

    private final PrescriptionLineRepository prescriptionLineRepository;
    private final PrescriptionOrderRepository prescriptionOrderRepository;
    private final DispenseRepository dispenseRepository;
    private final DispenseLineRepository dispenseLineRepository;
    private final InventoryFacade inventoryFacade;
    private final BillingFacade billingFacade;

    public DispenseService(
            PrescriptionLineRepository prescriptionLineRepository,
            PrescriptionOrderRepository prescriptionOrderRepository,
            DispenseRepository dispenseRepository,
            DispenseLineRepository dispenseLineRepository,
            InventoryFacade inventoryFacade,
            BillingFacade billingFacade
    ) {
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.dispenseRepository = dispenseRepository;
        this.dispenseLineRepository = dispenseLineRepository;
        this.inventoryFacade = inventoryFacade;
        this.billingFacade = billingFacade;
    }

    @Transactional
    public DispenseResult dispense(DispenseCommand command) {
        // §5.4/§5.5: "A repeat returns the original... does not create a
        // second or throw." Checked first so a retried request never
        // re-validates or re-processes anything — same PostPaymentService pattern.
        var existingDispense = dispenseRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existingDispense.isPresent()) {
            Dispense existing = existingDispense.get();
            PrescriptionLine line = prescriptionLineRepository.findById(existing.prescriptionLineId()).orElseThrow();
            return new DispenseResult(existing, dispenseLineRepository.findByDispenseId(existing.id()), line);
        }

        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("At least one dispense line is required.");
        }

        PrescriptionLine prescriptionLine = prescriptionLineRepository.findByIdForUpdate(command.prescriptionLineId())
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + command.prescriptionLineId()));
        UUID facilityId = prescriptionOrderRepository.findById(prescriptionLine.prescriptionOrderId())
                .orElseThrow(() -> new NoSuchElementException("Prescription order not found"))
                .facilityId();

        // Normalize each requested package quantity before FEFO, billing and stock mutation.
        // The selected factor is saved on the dispense line, so later packaging edits
        // cannot reinterpret this transaction.
        record Normalized(DispenseLineInput input, int baseQuantity, int factor, String unit) {}
        List<Normalized> normalized = command.lines().stream().map(input -> {
            if (input.quantity() <= 0) throw new IllegalArgumentException("Dispense quantity must be positive");
            String unit = input.requestedUnit() == null
                    ? inventoryFacade.findItemSummary(prescriptionLine.itemId()).orElseThrow().unit()
                    : input.requestedUnit();
            int factor = inventoryFacade.conversionFactor(prescriptionLine.itemId(), unit);
            return new Normalized(input, Math.multiplyExact(input.quantity(), factor), factor, unit);
        }).toList();
        List<DispenseLineInput> baseLines = normalized.stream().map(n ->
                new DispenseLineInput(n.input().batchId(), n.baseQuantity(), n.input().overrideReason())).toList();
        int totalQuantity = baseLines.stream().map(DispenseLineInput::quantity)
                .reduce(0, Math::addExact);
        // §4.4's three-way rule — pure, no side effects, before anything is touched.
        PrescriptionLine updatedLine = prescriptionLine.applyDispense(totalQuantity);

        // Evaluated live — the item's regulatory_schedule could have
        // changed to CONTROLLED since it was prescribed.
        if (!inventoryFacade.isDispensable(prescriptionLine.itemId())) {
            throw new ItemNotDispensableException(prescriptionLine.itemId());
        }

        for (DispenseLineInput input : baseLines) {
            inventoryFacade.assertBatchFacility(input.batchId(), facilityId);
        }
        requireOverrideReasonIfFefoWasNotFollowed(prescriptionLine.itemId(), facilityId, baseLines, totalQuantity);

        Map<UUID, Integer> quantitiesByBatch = new HashMap<>();
        baseLines.forEach(line -> quantitiesByBatch.merge(line.batchId(), line.quantity(), Math::addExact));
        inventoryFacade.assertDispensePackaging(prescriptionLine.itemId(), quantitiesByBatch);

        // §5.1: evaluated NOW, never cached from prescribing time. Throws
        // BillingGateRejectedException (-> 402) if unsatisfied; nothing
        // above this line has written anything.
        billingFacade.authorizeConsumption(prescriptionLine.invoiceLineId(), command.actorUserId());

        Dispense dispense = Dispense.create(prescriptionLine.id(), command.actorUserId(), command.idempotencyKey(),
                totalQuantity, command.counsellingNotes(), command.dispensingNotes());
        Dispense savedDispense;
        try {
            savedDispense = dispenseRepository.insert(dispense);
        } catch (DuplicateKeyException raced) {
            // Idempotency key UNIQUE index lost the race to a concurrent
            // identical request — return that one, not a second dispense.
            Dispense existing = dispenseRepository.findByIdempotencyKey(command.idempotencyKey()).orElseThrow();
            PrescriptionLine line = prescriptionLineRepository.findById(existing.prescriptionLineId()).orElseThrow();
            return new DispenseResult(existing, dispenseLineRepository.findByDispenseId(existing.id()), line);
        }

        List<DispenseLine> savedLines = new ArrayList<>();
        for (Normalized n : normalized) {
            DispenseLineInput input = n.input();
            UUID dispenseLineId = UUID.randomUUID();
            // Decrements the materialised balance (InsufficientStockException
            // on the CHECK (quantity >= 0) guard), hard-blocks an expired
            // or non-ACTIVE batch (BatchExpiredException), and returns the
            // batch's current unit cost to snapshot.
            Money unitCostSnapshot = inventoryFacade.decrementForDispense(
                    prescriptionLine.itemId(), input.batchId(), n.baseQuantity(), dispenseLineId, command.actorUserId());

            DispenseLine line = DispenseLine.create(savedDispense.id(), prescriptionLine.itemId(), input.batchId(),
                    n.baseQuantity(), unitCostSnapshot, input.overrideReason()).withId(dispenseLineId)
                    .withUnitSnapshot(input.quantity(), n.unit(), n.factor());
            savedLines.add(dispenseLineRepository.insert(line));
        }

        PrescriptionLine savedLine = prescriptionLineRepository.update(updatedLine);

        return new DispenseResult(savedDispense, savedLines, savedLine);
    }

    // §5.3: "FEFO pre-selected, override with a reason." If the submitted
    // batch selection differs from what FEFO would suggest for the same
    // total quantity, every submitted line must carry a non-blank reason
    // — attributing the deviation to one specific line would require
    // guessing which one "caused" it, and a blanket requirement is no
    // easier to satisfy honestly.
    private void requireOverrideReasonIfFefoWasNotFollowed(UUID itemId, UUID facilityId,
                                                             List<DispenseLineInput> lines, int totalQuantity) {
        List<BatchAvailability> available = inventoryFacade.findFefoBatches(itemId, facilityId);
        List<BatchAllocation> suggested;
        try {
            suggested = FefoAllocator.allocate(itemId, available, totalQuantity);
        } catch (InsufficientBatchStockException noFefoSuggestionPossible) {
            // findFefoBatches only returns ACTIVE, not-yet-expired batches
            // — if the *active* pool alone can't cover totalQuantity
            // (e.g. the caller is deliberately drawing from an expired or
            // quarantined batch), there is no FEFO suggestion to compare
            // against. Don't let that mask the real error: fall through
            // and let per-line InventoryFacade.decrementForDispense raise
            // the specific problem (BatchExpiredException,
            // InsufficientStockException) instead of a generic "not
            // enough stock" here.
            return;
        }

        Map<UUID, Integer> suggestedByBatch = new HashMap<>();
        suggested.forEach(a -> suggestedByBatch.merge(a.batchId(), a.quantity(), Integer::sum));

        Map<UUID, Integer> submittedByBatch = new HashMap<>();
        lines.forEach(l -> submittedByBatch.merge(l.batchId(), l.quantity(), Integer::sum));

        if (suggestedByBatch.equals(submittedByBatch)) {
            return;
        }
        boolean anyMissingReason = lines.stream().anyMatch(l -> l.overrideReason() == null || l.overrideReason().isBlank());
        if (anyMissingReason) {
            throw new MissingFefoOverrideReasonException();
        }
    }
}
