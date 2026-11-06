package com.adags.modules.laboratory.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.MissingQualityNoteException;
import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenQuality;
import com.adags.modules.laboratory.domain.SpecimenRepository;
import com.adags.modules.laboratory.domain.SpecimenStatus;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.numbering.GaplessNumberAllocator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 15-lab-technician.md §4: accession + quality assessment as one "accept
// sample" action (see V21__lab_order_and_specimen.sql for why they aren't
// split into separate collect/receive steps in this phase), and — the
// Phase 4 build brief's item 3 — THE GATE AT SPECIMEN COLLECTION. L1: "the
// billing gate sits at specimen collection, not result release." The gate
// runs before an accession number is allocated: an unpaid attempt must not
// burn a number that then never gets used (§4.1's whole point is that
// every number on a label corresponds to a real, processable specimen).
@Service
public class CollectSpecimenService {

    private static final String ACCESSION_SEQUENCE = "SPECIMEN_ACCESSION";
    private static final int ACCESSION_PAD_WIDTH = 6;
    private static final String SPECIMEN_ACCEPTED_EVENT = "SpecimenAccepted";
    private static final String SPECIMEN_REJECTED_EVENT = "SpecimenRejected";

    private final LabOrderRepository labOrderRepository;
    private final LabTestRepository labTestRepository;
    private final SpecimenRepository specimenRepository;
    private final BillingFacade billingFacade;
    private final GaplessNumberAllocator numberAllocator;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public CollectSpecimenService(
            LabOrderRepository labOrderRepository,
            LabTestRepository labTestRepository,
            SpecimenRepository specimenRepository,
            BillingFacade billingFacade,
            GaplessNumberAllocator numberAllocator,
            DomainEventPublisher eventPublisher,
            Clock clock
    ) {
        this.labOrderRepository = labOrderRepository;
        this.labTestRepository = labTestRepository;
        this.specimenRepository = specimenRepository;
        this.billingFacade = billingFacade;
        this.numberAllocator = numberAllocator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Specimen collect(CollectSpecimenCommand command) {
        if (command.quality() != SpecimenQuality.ADEQUATE
                && (command.qualityNote() == null || command.qualityNote().isBlank())) {
            throw new MissingQualityNoteException(command.quality());
        }

        LabOrder order = labOrderRepository.findById(command.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + command.labOrderId()));

        LabTest labTest = labTestRepository.findById(order.labTestId())
                .orElseThrow(() -> new NoSuchElementException("Lab test not found: " + order.labTestId()));

        String expectedType = labTest.specimenType();
        String requestedType = command.sampleType() == null ? expectedType : command.sampleType().trim();
        if (requestedType == null || requestedType.isBlank() || requestedType.equals("—"))
            throw new IllegalArgumentException("Enter the assessed sample type before acceptance.");
        boolean differsFromCatalogue = !requestedType.equalsIgnoreCase(expectedType);
        if (differsFromCatalogue &&
                (command.sampleTypeOverrideReason() == null || command.sampleTypeOverrideReason().isBlank()))
            throw new IllegalArgumentException("Explain why the assessed sample type differs from the test catalogue.");
        // §L1/§L2: evaluated live, at the point of consumption. Throws
        // BillingGateRejectedException (-> 402) if unsatisfied. Durable and
        // idempotent per §3.1 clause 4 — a second specimen against the same
        // order (recollection after a quality rejection) hits an
        // already-authorized line and is a no-op here, never re-charged
        // and never re-blocked (§4.3: "recollection is not re-charged").
        billingFacade.authorizeConsumption(order.invoiceLineId(), command.actorUserId());

        String accessionNo = numberAllocator.allocate(
                ACCESSION_SEQUENCE, order.facilityId(), LocalDate.now(clock).getYear(), ACCESSION_PAD_WIDTH);

        Instant now = Instant.now(clock);
        Specimen specimen = Specimen.accept(order.id(), accessionNo, requestedType, labTest.containerType(),
                command.actorUserId(), now, command.quality(), command.qualityNote(),
                differsFromCatalogue ? command.sampleTypeOverrideReason().trim() : null);
        Specimen saved = specimenRepository.insert(specimen);

        publishOutcomeEvent(saved, order);

        return saved;
    }

    private void publishOutcomeEvent(Specimen specimen, LabOrder order) {
        if (specimen.status() == SpecimenStatus.REJECTED) {
            // §4.3: reason recorded, ordering doctor notified, recollection
            // requested. Doctor notification is a future notifications-
            // module concern (doesn't exist yet) — this publishes the
            // event that consumer will subscribe to, same honest-stub
            // treatment as BillingGateImpl's verifiedInsuranceCovers.
            // patientSafetyIncident additionally routes MISLABELLED to
            // CLINICAL_LEAD per §4.3.
            eventPublisher.publish(SPECIMEN_REJECTED_EVENT, Map.of(
                    "specimenId", specimen.id().toString(),
                    "labOrderId", order.id().toString(),
                    "orderedBy", order.orderedBy().toString(),
                    "quality", specimen.quality().name(),
                    "rejectionReason", specimen.rejectionReason(),
                    "patientSafetyIncident", specimen.quality().isPatientSafetyIncident()));
        } else {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("specimenId", specimen.id().toString());
            payload.put("labOrderId", order.id().toString());
            payload.put("accessionNo", specimen.accessionNo());
            payload.put("sampleType", specimen.sampleType());
            if (specimen.sampleTypeOverrideReason() != null)
                payload.put("sampleTypeOverrideReason", specimen.sampleTypeOverrideReason());
            eventPublisher.publish(SPECIMEN_ACCEPTED_EVENT, payload);
        }
    }
}
