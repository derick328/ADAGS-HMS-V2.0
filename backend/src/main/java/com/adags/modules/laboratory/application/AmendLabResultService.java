package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.CriticalResultNotification;
import com.adags.modules.laboratory.domain.CriticalResultNotificationRepository;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultParameterValue;
import com.adags.modules.laboratory.domain.LabResultParameterValueRepository;
import com.adags.modules.laboratory.domain.LabResultReagentRepository;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestParameterRepository;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.MissingAmendmentReasonException;
import com.adags.modules.laboratory.domain.MissingMachineOutputException;
import com.adags.modules.laboratory.domain.ResultFlag;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.PatientDemographics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 16-lab-verification-model.md v0.1 §5 (unchanged by v0.2): "Amendment
// creates a NEW result row with amends_id. Original remains readable.
// Reason required. Ordering doctor notified that a result they may have
// acted on has changed." The original row is never updated in place —
// this only ever inserts a new row and flips the original's
// verification_state to AMENDED (LabResultRepository.markAmended).
@Service
public class AmendLabResultService {

    private static final String LAB_RESULT_AMENDED_EVENT = "LabResultAmended";
    private static final String CRITICAL_LAB_RESULT_RAISED_EVENT = "CriticalLabResultRaised";

    private final LabResultRepository labResultRepository;
    private final LabResultParameterValueRepository labResultParameterValueRepository;
    private final LabResultReagentRepository labResultReagentRepository;
    private final CriticalResultNotificationRepository criticalResultNotificationRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabTestRepository labTestRepository;
    private final LabTestParameterRepository labTestParameterRepository;
    private final LabResultAssembler labResultAssembler;
    private final PatientFacade patientFacade;
    private final DomainEventPublisher eventPublisher;

    public AmendLabResultService(
            LabResultRepository labResultRepository,
            LabResultParameterValueRepository labResultParameterValueRepository,
            LabResultReagentRepository labResultReagentRepository,
            CriticalResultNotificationRepository criticalResultNotificationRepository,
            LabOrderRepository labOrderRepository,
            LabTestRepository labTestRepository,
            LabTestParameterRepository labTestParameterRepository,
            LabResultAssembler labResultAssembler,
            PatientFacade patientFacade,
            DomainEventPublisher eventPublisher
    ) {
        this.labResultRepository = labResultRepository;
        this.labResultParameterValueRepository = labResultParameterValueRepository;
        this.labResultReagentRepository = labResultReagentRepository;
        this.criticalResultNotificationRepository = criticalResultNotificationRepository;
        this.labOrderRepository = labOrderRepository;
        this.labTestRepository = labTestRepository;
        this.labTestParameterRepository = labTestParameterRepository;
        this.labResultAssembler = labResultAssembler;
        this.patientFacade = patientFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RecordLabResultResult amend(AmendLabResultCommand command) {
        if (command.amendmentReason() == null || command.amendmentReason().isBlank()) {
            throw new MissingAmendmentReasonException();
        }

        LabResult original = labResultRepository.findById(command.originalResultId())
                .orElseThrow(() -> new NoSuchElementException("Lab result not found: " + command.originalResultId()));
        LabOrder order = labOrderRepository.findById(original.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + original.labOrderId()));
        LabTest labTest = labTestRepository.findById(order.labTestId())
                .orElseThrow(() -> new NoSuchElementException("Lab test not found: " + order.labTestId()));

        if (labTest.producesMachineOutput()
                && (command.machineOutputObjectKey() == null || command.machineOutputObjectKey().isBlank())) {
            throw new MissingMachineOutputException(labTest.code());
        }

        PatientDemographics demographics = patientFacade.findDemographics(order.patientId())
                .orElseThrow(() -> new NoSuchElementException("Patient not found: " + order.patientId()));
        List<LabResultAssembler.Evaluated> evaluated = labResultAssembler.evaluate(
                labTestParameterRepository.findByTestId(order.labTestId()), command.parameterValues(), demographics);

        // Fresh entry, its own current-verification-level snapshot — an
        // amendment is an entry event like any other (see class comment).
        LabResult amendment = labResultRepository.insert(LabResult.create(
                order.id(), original.specimenId(), command.amendedBy(), labTest.verificationLevel(),
                command.methodology(), command.reagentsUsedNote(), command.findings(), command.interpretation(),
                command.referenceRangeNote(), command.conclusion(), command.machineOutputObjectKey(),
                original.id(), command.amendmentReason()));

        labResultRepository.markAmended(original.id());

        List<LabResultParameterValue> values = new ArrayList<>();
        for (LabResultAssembler.Evaluated e : evaluated) {
            values.add(labResultParameterValueRepository.insert(LabResultParameterValue.create(
                    amendment.id(), e.parameterId(), e.resultValue(), e.flag(), e.overridden(), e.overrideReason(), e.appliedRangeId())));
        }
        if (command.reagents() != null) {
            for (ReagentEntry reagent : command.reagents()) {
                labResultReagentRepository.insert(com.adags.modules.laboratory.domain.LabResultReagent.create(
                        amendment.id(), reagent.stockBatchId(), reagent.quantityUsed(), reagent.note()));
            }
        }

        List<CriticalResultNotification> criticalNotifications = new ArrayList<>();
        for (LabResultParameterValue value : values) {
            if (value.flag() != ResultFlag.CRITICAL) {
                continue;
            }
            criticalNotifications.add(criticalResultNotificationRepository.insert(
                    CriticalResultNotification.raise(amendment.id(), value.parameterId(), value.resultValue(), order.orderedBy())));
        }
        if (!criticalNotifications.isEmpty()) {
            eventPublisher.publish(CRITICAL_LAB_RESULT_RAISED_EVENT, Map.of(
                    "resultId", amendment.id().toString(),
                    "notifiedUserId", order.orderedBy().toString(),
                    "criticalCount", criticalNotifications.size()));
        }

        // §5: "a silently corrected result is worse than an uncorrected
        // one" — this is the notification that it changed at all, separate
        // from the critical-value alert above (which fires only if the
        // corrected value is itself critical).
        eventPublisher.publish(LAB_RESULT_AMENDED_EVENT, Map.of(
                "originalResultId", original.id().toString(),
                "amendedResultId", amendment.id().toString(),
                "orderedBy", order.orderedBy().toString(),
                "reason", command.amendmentReason()));

        return new RecordLabResultResult(amendment, values, criticalNotifications);
    }
}
