package com.adags.modules.surgery.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryUrgency;
import com.adags.modules.surgery.domain.Theatre;
import com.adags.modules.surgery.domain.TheatreRepository;
import com.adags.shared.kernel.EncounterContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

// Phase 8 item 1: SurgeryOrder creation. Invariants applying here (root
// CLAUDE.md):
//  - Billing: "Nobody types a price" — procedure_name is resolved and
//    snapshotted server-side from the catalogue (BillingFacade.
//    findActiveProcedureName), never trusted from the client, then the
//    procedure fee is posted through the one billing gate entry point
//    (postServiceCharge) exactly like every other charge in this codebase.
//  - Billing: "IMMEDIATE triage priority bypasses the payment gate for the
//    whole encounter" — this module's own IMMEDIATE urgency does the same
//    via createImmediateSurgeryPaymentBypass, scoped to the encounter, so
//    BillingGateImpl's existing encounter-scoped bypass lookup covers the
//    procedure-start gate (a later item) without any gate change.
//  - Task text: "Consent blocks SCHEDULING, not billing. No signed
//    document, no theatre slot." Enforced by SurgeryOrder.schedule before
//    a theatre slot is ever claimed — no charge is posted and no slot is
//    reserved for an order that fails this check.
//  - Concurrency: "a read-then-write check loses the race" — the theatre
//    slot claim is the repository insert against
//    idx_theatre_slot_not_cancelled; this service does not pre-check
//    availability, since that check would itself be the race.
@Service
public class ScheduleSurgeryService {

    private final SurgeryOrderRepository surgeryOrderRepository;
    private final TheatreRepository theatreRepository;
    private final EncounterFacade encounterFacade;
    private final BillingFacade billingFacade;

    public ScheduleSurgeryService(
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
    public SurgeryOrder schedule(ScheduleSurgeryCommand command) {
        EncounterContext context = encounterFacade.findContext(command.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + command.encounterId()));

        Theatre theatre = theatreRepository.findById(command.theatreId())
                .orElseThrow(() -> new NoSuchElementException("Theatre not found: " + command.theatreId()));
        if (!theatre.facilityId().equals(context.facilityId())) {
            throw new IllegalArgumentException(
                    "Theatre " + command.theatreId() + " does not belong to the encounter's facility.");
        }

        String procedureName = billingFacade.findActiveProcedureName(command.procedureServiceItemCode())
                .orElseThrow(() -> new NoSuchElementException(
                        "No active procedure catalogue item: " + command.procedureServiceItemCode()));

        // Consent is checked before any charge is posted or slot claimed —
        // SurgeryOrder.schedule throws ConsentNotObtainedException first.
        UUID surgeryOrderId = UUID.randomUUID();
        SurgeryOrder toInsert = SurgeryOrder.schedule(
                command.encounterId(), command.procedureServiceItemCode(), procedureName, command.surgeryType(),
                command.urgency(), command.anesthesiaType(), command.scheduledDate(), command.timeSlot(),
                command.theatreId(), command.estimatedDurationMinutes(), command.assignedNurses(),
                command.preOpNotes(), command.consentObtained(), command.consentDocumentKey(),
                postProcedureCharge(context, command, surgeryOrderId), command.actorUserId())
                .withId(surgeryOrderId);

        if (command.urgency() == SurgeryUrgency.IMMEDIATE) {
            // root CLAUDE.md "Safety": "Theatre proceeds, charges accrue to
            // the emergency balance" — the bypass, not a waived charge; the
            // procedure fee above is posted the same as any other case.
            billingFacade.createImmediateSurgeryPaymentBypass(
                    command.encounterId(), "Surgery urgency IMMEDIATE", command.actorUserId());
        }

        return surgeryOrderRepository.insert(toInsert);
    }

    // Posted before the SurgeryOrder row exists, keyed to its
    // pre-generated id — same ordering as
    // SubmitTriageAssessmentService posting the consultation charge against
    // a pre-generated triageAssessmentId. Runs inside this method's own
    // @Transactional: a price-resolution failure (PriceNotConfiguredException)
    // rolls back before any theatre slot is claimed.
    private UUID postProcedureCharge(EncounterContext context, ScheduleSurgeryCommand command, UUID surgeryOrderId) {
        return billingFacade.postServiceCharge(
                command.encounterId(), context.patientId(), context.facilityId(), context.payerType(),
                command.procedureServiceItemCode(), 1, "SurgeryOrderScheduled", surgeryOrderId,
                command.actorUserId());
    }
}
