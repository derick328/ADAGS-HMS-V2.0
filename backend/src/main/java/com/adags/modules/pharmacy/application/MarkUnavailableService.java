package com.adags.modules.pharmacy.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.shared.events.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §5.2: "Pharmacist marks the line UNAVAILABLE with a
// reason -> doctor notified to re-prescribe or cancel. Already paid:
// refund the line through the accountant path. Unpaid: charge voided
// automatically." Doctor notification is a future notifications-module
// concern (doesn't exist yet) — this publishes the event a consumer will
// subscribe to, the same honest-stub treatment SpecimenRejected gets.
@Service
public class MarkUnavailableService {

    private static final String EVENT = "PrescriptionItemUnavailable";

    private final PrescriptionLineRepository prescriptionLineRepository;
    private final BillingFacade billingFacade;
    private final DomainEventPublisher eventPublisher;

    public MarkUnavailableService(
            PrescriptionLineRepository prescriptionLineRepository,
            BillingFacade billingFacade,
            DomainEventPublisher eventPublisher
    ) {
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.billingFacade = billingFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PrescriptionLine markUnavailable(UUID prescriptionLineId, String reason, UUID actorUserId) {
        PrescriptionLine line = prescriptionLineRepository.findById(prescriptionLineId)
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + prescriptionLineId));

        PrescriptionLine updated = line.markUnavailable(reason);
        PrescriptionLine saved = prescriptionLineRepository.update(updated);

        // false means something was already settled against this charge —
        // left for the accountant's (not-yet-built) refund path, per the
        // spec's own "refund the line through the accountant path" for
        // that case; refundNeeded in the event payload flags it for
        // whichever consumer eventually handles that.
        boolean voided = billingFacade.voidUnpaidServiceCharge(line.invoiceLineId(), "Item unavailable: " + reason, actorUserId);

        eventPublisher.publish(EVENT, Map.of(
                "prescriptionLineId", saved.id().toString(),
                "itemId", saved.itemId().toString(),
                "reason", reason,
                "refundNeeded", String.valueOf(!voided)));

        return saved;
    }
}
