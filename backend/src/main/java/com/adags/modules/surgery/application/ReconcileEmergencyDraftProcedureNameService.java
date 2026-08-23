package com.adags.modules.surgery.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.surgery.domain.InvalidSurgeryOrderTransitionException;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
import com.adags.shared.kernel.EncounterContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

// Phase 8 item 8: "Free-text procedure names must be reconciled to a
// catalogue item before the charge can be finalised." This is that
// reconciliation — resolves the surgeon-supplied catalogue code the same
// way ScheduleSurgeryService does (BillingFacade.findActiveProcedureName,
// never trusting a client-supplied name), posts the procedure fee that
// never got posted at draft-creation time, and rejoins the normal
// pipeline: DRAFT_EMERGENCY -> IN_PROGRESS, from which
// checklist/reconciliation/completion apply exactly as they do to a
// normally-scheduled order.
@Service
public class ReconcileEmergencyDraftProcedureNameService {

    private final SurgeryOrderRepository surgeryOrderRepository;
    private final EncounterFacade encounterFacade;
    private final BillingFacade billingFacade;

    public ReconcileEmergencyDraftProcedureNameService(
            SurgeryOrderRepository surgeryOrderRepository, EncounterFacade encounterFacade, BillingFacade billingFacade
    ) {
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.encounterFacade = encounterFacade;
        this.billingFacade = billingFacade;
    }

    @Transactional
    public SurgeryOrder reconcile(ReconcileEmergencyDraftProcedureNameCommand command) {
        SurgeryOrder order = surgeryOrderRepository.findById(command.surgeryOrderId())
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + command.surgeryOrderId()));
        if (order.status() != SurgeryOrderStatus.DRAFT_EMERGENCY) {
            throw new InvalidSurgeryOrderTransitionException(order.id(), order.status(), SurgeryOrderStatus.IN_PROGRESS);
        }

        String procedureName = billingFacade.findActiveProcedureName(command.procedureServiceItemCode())
                .orElseThrow(() -> new NoSuchElementException(
                        "No active procedure catalogue item: " + command.procedureServiceItemCode()));

        EncounterContext context = encounterFacade.findContext(order.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + order.encounterId()));
        UUID invoiceLineId = billingFacade.postServiceCharge(order.encounterId(), context.patientId(),
                context.facilityId(), context.payerType(), command.procedureServiceItemCode(), 1,
                "SurgeryOrderScheduled", order.id(), command.actorUserId());

        boolean reconciled = surgeryOrderRepository.reconcileEmergencyDraft(
                order.id(), command.procedureServiceItemCode(), procedureName, invoiceLineId);
        if (!reconciled) {
            throw new InvalidSurgeryOrderTransitionException(order.id(), order.status(), SurgeryOrderStatus.IN_PROGRESS);
        }

        return surgeryOrderRepository.findById(order.id()).orElseThrow();
    }
}
