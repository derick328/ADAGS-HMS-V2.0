package com.adags.modules.laboratory.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.EncounterContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 15-lab-technician.md §4/§12 (LAB_ORDER_CREATE), 02-billing-architecture.md
// §4: "every charge is posted by a domain event." Mirrors
// SubmitTriageAssessmentService's step 6 — the charge is posted inside the
// same transaction that creates the order, so a lab order with no charge
// is not a state this system can reach (root CLAUDE.md "Billing").
//
// One LabOrder = one LabTest = one invoice line (see
// V21__lab_order_and_specimen.sql) — "gating is per invoice line, never
// per invoice" is therefore structural here, not something this service
// has to enforce.
@Service
public class PlaceLabOrderService {

    private static final String LAB_TEST_ORDERED_EVENT = "LabTestOrdered";

    private final LabOrderRepository labOrderRepository;
    private final LabTestRepository labTestRepository;
    private final EncounterFacade encounterFacade;
    private final BillingFacade billingFacade;
    private final DomainEventPublisher eventPublisher;

    public PlaceLabOrderService(
            LabOrderRepository labOrderRepository,
            LabTestRepository labTestRepository,
            EncounterFacade encounterFacade,
            BillingFacade billingFacade,
            DomainEventPublisher eventPublisher
    ) {
        this.labOrderRepository = labOrderRepository;
        this.labTestRepository = labTestRepository;
        this.encounterFacade = encounterFacade;
        this.billingFacade = billingFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public LabOrder placeOrder(PlaceLabOrderCommand command) {
        LabTest labTest = labTestRepository.findByCode(command.labTestCode())
                .filter(LabTest::isActive)
                .orElseThrow(() -> new NoSuchElementException(
                        "No active lab test with code '" + command.labTestCode() + "'."));

        EncounterContext encounterContext = encounterFacade.findContext(command.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + command.encounterId()));

        UUID labOrderId = UUID.randomUUID();

        // The charge is posted before the order row exists, same ordering
        // SubmitTriageAssessmentService uses — if pricing fails (no
        // service_item for this test's code, or no current price for the
        // encounter's payer category), the whole transaction rolls back
        // and no order is left dangling with nothing to gate against.
        UUID invoiceLineId = billingFacade.postServiceCharge(
                command.encounterId(), encounterContext.patientId(), encounterContext.facilityId(),
                encounterContext.payerType(), labTest.code(), 1, LAB_TEST_ORDERED_EVENT, labOrderId,
                command.orderedBy());

        LabOrder order = LabOrder.create(
                command.encounterId(), encounterContext.patientId(), encounterContext.facilityId(),
                labTest.id(), command.orderedBy(), command.clinicalIndication(), command.specialInstructions(), command.urgency(), invoiceLineId
        ).withId(labOrderId);
        LabOrder saved = labOrderRepository.insert(order);

        eventPublisher.publish(LAB_TEST_ORDERED_EVENT, Map.of(
                "labOrderId", saved.id().toString(),
                "encounterId", saved.encounterId().toString(),
                "patientId", saved.patientId().toString(),
                "labTestCode", labTest.code(),
                "urgency", saved.urgency().name(),
                "invoiceLineId", invoiceLineId.toString()));

        return saved;
    }
}
