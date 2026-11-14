package com.adags.modules.pharmacy.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.clinical.ClinicalFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.InpatientFacade;
import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.pharmacy.domain.ItemNotDispensableException;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionUnitSnapshot;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.shared.kernel.DosageForm;
import com.adags.modules.pharmacy.domain.PrescriptionFrequency;
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
    private final ClinicalFacade clinicalFacade;
    private final DomainEventPublisher eventPublisher;

    public PlacePrescriptionService(
            PrescriptionOrderRepository prescriptionOrderRepository,
            PrescriptionLineRepository prescriptionLineRepository,
            EncounterFacade encounterFacade,
            InventoryFacade inventoryFacade,
            BillingFacade billingFacade,
            InpatientFacade inpatientFacade,
            ClinicalFacade clinicalFacade,
            DomainEventPublisher eventPublisher
    ) {
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.encounterFacade = encounterFacade;
        this.inventoryFacade = inventoryFacade;
        this.billingFacade = billingFacade;
        this.inpatientFacade = inpatientFacade;
        this.clinicalFacade = clinicalFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PrescriptionLine placeOrder(PlacePrescriptionCommand command) {
        ItemSummary item = inventoryFacade.findItemSummary(command.itemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + command.itemId()));
        if (!item.dispensable()) {
            throw new ItemNotDispensableException(command.itemId());
        }

        int requestedQuantity = command.requestedQuantity() == null
                ? command.quantityPrescribed() : command.requestedQuantity();
        String requestedUnit = command.requestedUnit() == null
                ? item.unit() : command.requestedUnit();
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("Requested quantity must be positive");
        }
        int factor = command.requestedUnit() == null ? 1
                : inventoryFacade.conversionFactor(command.itemId(), requestedUnit);
        if (Math.multiplyExact(requestedQuantity, factor) != command.quantityPrescribed()) {
            throw new IllegalArgumentException("Prescribed base quantity does not match selected package unit");
        }

        EncounterContext encounterContext = encounterFacade.findContext(command.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + command.encounterId()));

        if (clinicalFacade.isPregnancyAssessmentApplicable(encounterContext.patientId())
                && clinicalFacade.findPregnancyStatus(command.encounterId()).isEmpty()) {
            throw new IllegalArgumentException("Choose pregnancy status (Yes, No, or Unknown) before prescribing.");
        }

        // Recheck at placement: the picker may have shown stock that was
        // dispensed or expired while the doctor completed the prescription.
        if (inventoryFacade.findFefoBatches(command.itemId(), encounterContext.facilityId()).stream()
                .noneMatch(batch -> batch.quantityAvailable() > 0)) {
            throw new IllegalArgumentException("No usable, unexpired stock is available for this medicine.");
        }

        validateQuantityPolicy(item, command, requestedQuantity, requestedUnit);
        var approvedRoutes = inventoryFacade.approvedAdministrationRoutes(command.itemId());
        String productStrength = DosageForm.fromCatalogue(item.form()) == DosageForm.INJECTION
                ? inventoryFacade.productStrength(command.itemId()) : null;
        if (DosageForm.fromCatalogue(item.form()) == DosageForm.INJECTION &&
                (approvedRoutes.isEmpty() || productStrength == null || productStrength.isBlank())) {
            throw new IllegalArgumentException("Injection requires a recorded concentration and approved administration routes.");
        }
        if (!approvedRoutes.isEmpty() && approvedRoutes.stream()
                .noneMatch(route -> route.equalsIgnoreCase(command.route().trim()))) {
            throw new IllegalArgumentException("Select a route approved for this medicine.");
        }

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
        prescriptionLineRepository.insertUnitSnapshot(new PrescriptionUnitSnapshot(savedLine.id(),
                requestedQuantity, requestedUnit, factor, item.unit(), item.form(),
                productStrength == null ? inventoryFacade.productStrength(command.itemId()) : productStrength));

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
        if (command.frequency() != null && command.frequency() != PrescriptionFrequency.PRN
                && command.frequency() != PrescriptionFrequency.OTHER
                && (command.durationDays() != null || command.frequency() == PrescriptionFrequency.STAT)) {
            inpatientFacade.findActiveAdmissionId(command.encounterId()).ifPresent(admissionId ->
                    inpatientFacade.generateMedicationAdministrationSchedule(admissionId, savedLine.id(),
                            command.frequency().name(), command.frequency() == PrescriptionFrequency.STAT
                                    ? 1 : command.durationDays(), savedOrder.prescribedAt()));
        }

        return savedLine;
    }

    static void validateQuantityPolicy(ItemSummary item, PlacePrescriptionCommand command,
                                               int requestedQuantity, String requestedUnit) {
        DosageForm form = DosageForm.fromCatalogue(item.form());
        if (form == null) {
            throw new IllegalArgumentException("Assign a supported dosage form to this medicine before prescribing.");
        }
        if (command.doseAmount() == null || !Double.isFinite(command.doseAmount())
                || command.doseAmount() <= 0 || command.doseUnit() == null || command.doseUnit().isBlank()
                || command.route() == null || command.route().isBlank() || command.frequency() == null) {
            throw new IllegalArgumentException("Dose amount, dose unit, route and frequency are required.");
        }
        PrescriptionFrequency frequency = command.frequency();
        if (frequency == PrescriptionFrequency.OTHER && (command.dosageInstructions() == null
                || command.dosageInstructions().isBlank()
                || command.dosageInstructions().trim().equalsIgnoreCase("As directed"))) {
            throw new IllegalArgumentException("Describe the nonstandard administration schedule.");
        }
        if (frequency != PrescriptionFrequency.STAT && frequency != PrescriptionFrequency.PRN
                && frequency != PrescriptionFrequency.OTHER
                && (command.durationDays() == null || command.durationDays() <= 0)) {
            throw new IllegalArgumentException("Duration in days is required for this schedule.");
        }
        if (form.automaticQuantity()) {
            if (!form.baseUnit().equalsIgnoreCase(item.unit())) {
                throw new IllegalArgumentException("Solid-dose catalogue base unit must match its dosage form.");
            }
            if (!command.doseUnit().trim().equalsIgnoreCase(item.unit())) {
                throw new IllegalArgumentException("Solid-dose unit must match the medicine's base unit.");
            }
            if (command.doseAmount() != Math.rint(command.doseAmount())
                    || command.doseAmount() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Solid-dose amount must be a whole unit.");
            }
            if (frequency != PrescriptionFrequency.PRN && frequency != PrescriptionFrequency.OTHER) {
                try {
                    int administrations = frequency == PrescriptionFrequency.STAT ? 1
                            : Math.multiplyExact(frequency.timesPerDay(), command.durationDays());
                    int expected = Math.multiplyExact(command.doseAmount().intValue(), administrations);
                    if (command.quantityPrescribed() != expected) {
                        throw new IllegalArgumentException("Dispensing quantity must equal dose units × administrations × days.");
                    }
                } catch (ArithmeticException overflow) {
                    throw new IllegalArgumentException("Calculated dispensing quantity is too large.", overflow);
                }
            } // PRN requires an explicit positive supply quantity, checked above.
        } else {
            if (command.requestedQuantity() == null || command.requestedUnit() == null
                    || command.requestedUnit().isBlank()) {
                throw new IllegalArgumentException("Manual dispensing quantity and approved supply unit are required.");
            }
            String unit = requestedUnit.trim().toUpperCase(java.util.Locale.ROOT);
            if (unit.equals("ML") || unit.equals("MILLILITRE") || unit.equals("G")
                    || unit.equals("GRAM") || unit.equals("UNIT")) {
                throw new IllegalArgumentException("Select an approved supply package, such as a bottle, vial or tube.");
            }
        }
    }
}
