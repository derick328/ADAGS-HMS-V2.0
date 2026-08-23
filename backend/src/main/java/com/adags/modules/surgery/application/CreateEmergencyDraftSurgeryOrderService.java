package com.adags.modules.surgery.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.Theatre;
import com.adags.modules.surgery.domain.TheatreRepository;
import com.adags.shared.kernel.EncounterContext;
import com.adags.modules.encounters.EncounterFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 8 item 8: "urgency = IMMEDIATE -> status = DRAFT_EMERGENCY,
// free-text fields permitted, billing gate bypassed." No consent check
// (SurgeryOrder.draftEmergency, unlike .schedule, never throws
// ConsentNotObtainedException), no catalogue lookup, no procedure-fee
// charge posted — "Surgeon prompted to complete post-operatively" is what
// ReconcileEmergencyDraftProcedureNameService is for.
//
// The PaymentBypass still posts here, immediately: root CLAUDE.md
// "Safety": "IMMEDIATE triage priority bypasses the payment gate for the
// whole encounter" — this is the surgery-side equivalent, and there is no
// reason to defer it to reconciliation time since urgency is always
// IMMEDIATE for a draft by definition.
@Service
public class CreateEmergencyDraftSurgeryOrderService {

    private final SurgeryOrderRepository surgeryOrderRepository;
    private final TheatreRepository theatreRepository;
    private final EncounterFacade encounterFacade;
    private final BillingFacade billingFacade;

    public CreateEmergencyDraftSurgeryOrderService(
            SurgeryOrderRepository surgeryOrderRepository,
            TheatreRepository theatreRepository,
            EncounterFacade encounterFacade,
            BillingFacade billingFacade
    ) {
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.theatreRepository = theatreRepository;
        this.encounterFacade = encounterFacade;
        this.billingFacade = billingFacade;
    }

    @Transactional
    public SurgeryOrder create(CreateEmergencyDraftSurgeryOrderCommand command) {
        EncounterContext context = encounterFacade.findContext(command.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + command.encounterId()));

        Theatre theatre = theatreRepository.findById(command.theatreId())
                .orElseThrow(() -> new NoSuchElementException("Theatre not found: " + command.theatreId()));
        if (!theatre.facilityId().equals(context.facilityId())) {
            throw new IllegalArgumentException(
                    "Theatre " + command.theatreId() + " does not belong to the encounter's facility.");
        }

        billingFacade.createImmediateSurgeryPaymentBypass(
                command.encounterId(), "Emergency surgery draft", command.actorUserId());

        SurgeryOrder toInsert = SurgeryOrder.draftEmergency(command.encounterId(), command.procedureName(),
                command.surgeryType(), command.anesthesiaType(), command.scheduledDate(), command.timeSlot(),
                command.theatreId(), command.estimatedDurationMinutes(), command.assignedNurses(),
                command.preOpNotes(), command.actorUserId());
        return surgeryOrderRepository.insert(toInsert);
    }
}
