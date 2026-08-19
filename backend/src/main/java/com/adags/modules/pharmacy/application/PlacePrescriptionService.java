package com.adags.modules.pharmacy.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.InpatientFacade;
import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.pharmacy.domain.ItemNotDispensableException;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.EncounterContext;
import com.adags.shared.kernel.ItemSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// D1 (12-pharmacist.md §1): no medicine is dispensed without a doctor's
// prescription. No orders/prescribing module exists in this codebase yet
// (confirmed by grep before this was built), so this is the same self-
// contained treatment PlaceLabOrderService gives lab orders inside
// laboratory — no substitution, no doctor UI, just enough to give
// dispensing something real to gate and decrement against. The charge is
// posted inside the same transaction that creates the order (mirrors
// PlaceLabOrderService exactly) so a prescription with no charge is not a
// state this system can reach (root CLAUDE.md "Billing").
//
// 14-controlled-medicines-tanzania.md §3: "POST /prescriptions Rejected
// server-side even when a valid item_id is submitted directly" — checked
// here via InventoryFacade.isDispensable, live, before anything is posted.
@Service
public class PlacePrescriptionService {

    private static final String PRESCRIPTION_ISSUED_EVENT = "PrescriptionIssued";

    private final PrescriptionOrderRepository prescriptionOrderRepository;
    private final PrescriptionLineRepository prescriptionLineRepository;
    private final EncounterFacade encounterFacade;
    private final InventoryFacade inventoryFacade;
    private final BillingFacade billingFacade;
    private final InpatientFacade inpatientFacade;
    private final DomainEventPublisher eventPublisher;

    public PlacePrescriptionService(
            PrescriptionOrderRepository prescriptionOrderRepository,
            PrescriptionLineRepository prescriptionLineRepository,
            EncounterFacade encounterFacade,
            InventoryFacade inventoryFacade,
            BillingFacade billingFacade,
            InpatientFacade inpatientFacade,
            DomainEventPublisher eventPublisher
    ) {
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.encounterFacade = encounterFacade;
        this.inventoryFacade = inventoryFacade;
        this.billingFacade = billingFacade;
        this.inpatientFacade = inpatientFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PrescriptionLine placeOrder(PlacePrescriptionCommand command) {
        ItemSummary item = inventoryFacade.findItemSummary(command.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + command.itemId()));
        if (!item.dispensable()) {
            throw new ItemNotDispensableException(command.itemId());
        }

        EncounterContext encounterContext = encounterFacade.findContext(command.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + command.encounterId()));

        UUID prescriptionOrderId = UUID.randomUUID();

        // Posted before the order/line rows exist — same ordering
        // PlaceLabOrderService uses, so a pricing failure (no service_item
        // for this item's code, or no current price for the payer
        // category) rolls the whole transaction back rather than leaving
        // a prescription with nothing to gate against.
        UUID invoiceLineId = billingFacade.postServiceCharge(
                command.encounterId(), encounterContext.patientId(), encounterContext.facilityId(),
                encounterContext.payerType(), item.code(), command.quantityPrescribed(), PRESCRIPTION_ISSUED_EVENT,
                prescriptionOrderId, command.prescribedBy());

        PrescriptionOrder order = PrescriptionOrder.create(command.encounterId(), encounterContext.patientId(),
                encounterContext.facilityId(), command.prescribedBy()).withId(prescriptionOrderId);
        PrescriptionOrder savedOrder = prescriptionOrderRepository.insert(order);

        PrescriptionLine line = PrescriptionLine.create(savedOrder.id(), command.itemId(),
                command.quantityPrescribed(), command.dosageInstructions(), invoiceLineId, command.substitutionAllowed(),
                command.route(), command.doseAmount(), command.doseUnit(), command.frequency(), command.durationDays());
        PrescriptionLine savedLine = prescriptionLineRepository.insert(line);

        eventPublisher.publish(PRESCRIPTION_ISSUED_EVENT, Map.of(
                "prescriptionOrderId", savedOrder.id().toString(),
                "prescriptionLineId", savedLine.id().toString(),
                "encounterId", savedOrder.encounterId().toString(),
                "patientId", savedOrder.patientId().toString(),
                "itemId", command.itemId().toString(),
                "invoiceLineId", invoiceLineId.toString()));

        // 17-ward-nurse.md §5.1: "system generates 15 slots" — a direct,
        // same-transaction call, not the outbox (nothing in this codebase
        // consumes the outbox today, and the nurse needs this schedule
        // this shift, not eventually). Only when the encounter is under an
        // active admission and the prescriber supplied a schedulable
        // frequency (PRN and a bare null both generate nothing — see
        // PrescriptionFrequency).
        if (command.frequency() != null && command.durationDays() != null) {
            inpatientFacade.findActiveAdmissionId(command.encounterId()).ifPresent(admissionId ->
                    inpatientFacade.generateMedicationAdministrationSchedule(admissionId, savedLine.id(),
                            command.frequency().name(), command.durationDays(), savedOrder.prescribedAt()));
        }

        return savedLine;
    }
}
