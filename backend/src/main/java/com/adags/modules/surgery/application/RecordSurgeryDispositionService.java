package com.adags.modules.surgery.application;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inpatient.InpatientFacade;
import com.adags.modules.surgery.domain.DispositionAlreadyRecordedException;
import com.adags.modules.surgery.domain.SurgeryOrder;
import com.adags.modules.surgery.domain.SurgeryOrderNotCompletedException;
import com.adags.modules.surgery.domain.SurgeryOrderRepository;
import com.adags.modules.surgery.domain.SurgeryOrderStatus;
import com.adags.shared.kernel.EncounterContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 8 item 5: "Procedure COMPLETE -> disposition" (04-procedure-
// bedday-rules.md §2.4). Runs strictly after CompleteSurgeryOrderService —
// order.status() must already be COMPLETED, the WHERE-guarded repository
// write is the race guard for that plus "recorded exactly once."
//
// ADMIT_TO_WARD: "bed-day accrual begins at sent-to-admission timestamp,
// NOT procedure end" — satisfied simply by calling
// InpatientFacade.admitFromProcedure here (admittedAt = now, called after
// COMPLETED), never at IN_PROGRESS start. No specific bed is assigned —
// AssignBedService (ward staff's own action) does that afterward, same as
// every other admission in this codebase.
@Service
public class RecordSurgeryDispositionService {

    private final SurgeryOrderRepository surgeryOrderRepository;
    private final EncounterFacade encounterFacade;
    private final InpatientFacade inpatientFacade;

    public RecordSurgeryDispositionService(
            SurgeryOrderRepository surgeryOrderRepository, EncounterFacade encounterFacade, InpatientFacade inpatientFacade
    ) {
        this.surgeryOrderRepository = surgeryOrderRepository;
        this.encounterFacade = encounterFacade;
        this.inpatientFacade = inpatientFacade;
    }

    @Transactional
    public SurgeryOrder recordDisposition(RecordSurgeryDispositionCommand command) {
        SurgeryOrder order = surgeryOrderRepository.findById(command.surgeryOrderId())
                .orElseThrow(() -> new NoSuchElementException("Surgery order not found: " + command.surgeryOrderId()));
        if (order.status() != SurgeryOrderStatus.COMPLETED) {
            throw new SurgeryOrderNotCompletedException(order.id());
        }
        if (order.disposition() != null) {
            throw new DispositionAlreadyRecordedException(order.id());
        }

        switch (command.disposition()) {
            case RECOVERY_THEN_DISCHARGE -> encounterFacade.moveToConsultComplete(order.encounterId(), command.actorUserId());
            case ADMIT_TO_WARD -> admitToWard(order, command);
            case TRANSFER_OUT -> encounterFacade.referOut(order.encounterId(), command.actorUserId());
            case DECEASED -> encounterFacade.markDeceased(order.encounterId(), command.actorUserId());
        }

        boolean recorded = surgeryOrderRepository.recordDisposition(order.id(), command.disposition(), command.actorUserId());
        if (!recorded) {
            throw new DispositionAlreadyRecordedException(order.id());
        }

        return surgeryOrderRepository.findById(order.id()).orElseThrow();
    }

    private void admitToWard(SurgeryOrder order, RecordSurgeryDispositionCommand command) {
        if (command.orderedWardClass() == null || command.orderedWardClass().isBlank()) {
            throw new IllegalArgumentException("orderedWardClass is required for ADMIT_TO_WARD.");
        }
        EncounterContext context = encounterFacade.findContext(order.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + order.encounterId()));
        encounterFacade.admit(order.encounterId(), command.actorUserId());
        inpatientFacade.admitFromProcedure(order.encounterId(), context.patientId(), context.facilityId(),
                order.scheduledBy(), command.orderedWardClass(), command.actorUserId());
    }
}
