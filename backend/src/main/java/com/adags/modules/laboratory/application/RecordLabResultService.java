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
import com.adags.modules.laboratory.domain.MissingMachineOutputException;
import com.adags.modules.laboratory.domain.ResultFlag;
import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenNotProcessableException;
import com.adags.modules.laboratory.domain.SpecimenRepository;
import com.adags.modules.laboratory.domain.SpecimenStatus;
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

// 15-lab-technician.md §5-§7. §L1/root CLAUDE.md "Safety": nothing here
// calls BillingFacade — the gate already ran once, durably, at specimen
// collection (CollectSpecimenService); result entry and release are never
// re-gated, which is what makes "critical results release regardless of
// payment status" structurally true rather than a rule this service has
// to remember to honour.
@Service
public class RecordLabResultService {

    private static final String LAB_RESULT_RELEASED_EVENT = "LabResultReleased";
    private static final String CRITICAL_LAB_RESULT_RAISED_EVENT = "CriticalLabResultRaised";

    private final SpecimenRepository specimenRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabTestRepository labTestRepository;
    private final LabTestParameterRepository labTestParameterRepository;
    private final LabResultRepository labResultRepository;
    private final LabResultParameterValueRepository labResultParameterValueRepository;
    private final LabResultReagentRepository labResultReagentRepository;
    private final CriticalResultNotificationRepository criticalResultNotificationRepository;
    private final LabResultAssembler labResultAssembler;
    private final PatientFacade patientFacade;
    private final DomainEventPublisher eventPublisher;

    public RecordLabResultService(
            SpecimenRepository specimenRepository,
            LabOrderRepository labOrderRepository,
            LabTestRepository labTestRepository,
            LabTestParameterRepository labTestParameterRepository,
            LabResultRepository labResultRepository,
            LabResultParameterValueRepository labResultParameterValueRepository,
            LabResultReagentRepository labResultReagentRepository,
            CriticalResultNotificationRepository criticalResultNotificationRepository,
            LabResultAssembler labResultAssembler,
            PatientFacade patientFacade,
            DomainEventPublisher eventPublisher
    ) {
        this.specimenRepository = specimenRepository;
        this.labOrderRepository = labOrderRepository;
        this.labTestRepository = labTestRepository;
        this.labTestParameterRepository = labTestParameterRepository;
        this.labResultRepository = labResultRepository;
        this.labResultParameterValueRepository = labResultParameterValueRepository;
        this.labResultReagentRepository = labResultReagentRepository;
        this.criticalResultNotificationRepository = criticalResultNotificationRepository;
        this.labResultAssembler = labResultAssembler;
        this.patientFacade = patientFacade;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RecordLabResultResult record(RecordLabResultCommand command) {
        Specimen specimen = specimenRepository.findById(command.specimenId())
                .orElseThrow(() -> new NoSuchElementException("Specimen not found: " + command.specimenId()));
        if (specimen.status() != SpecimenStatus.IN_PROGRESS) {
            throw new SpecimenNotProcessableException(specimen.id(), specimen.status());
        }

        LabOrder order = labOrderRepository.findById(specimen.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + specimen.labOrderId()));
        LabTest labTest = labTestRepository.findById(order.labTestId())
                .orElseThrow(() -> new NoSuchElementException("Lab test not found: " + order.labTestId()));

        // 16-lab-verification-model.md v0.2 §4: without this, the result is
        // permanently unverifiable — nothing to compare typed values against.
        if (labTest.producesMachineOutput()
                && (command.machineOutputObjectKey() == null || command.machineOutputObjectKey().isBlank())) {
            throw new MissingMachineOutputException(labTest.code());
        }

        PatientDemographics demographics = patientFacade.findDemographics(order.patientId())
                .orElseThrow(() -> new NoSuchElementException("Patient not found: " + order.patientId()));
        List<LabResultAssembler.Evaluated> evaluated = labResultAssembler.evaluate(
                labTestParameterRepository.findByTestId(order.labTestId()), command.parameterValues(), demographics);

        LabResult saved = labResultRepository.insert(LabResult.create(
                order.id(), specimen.id(), command.enteredBy(), labTest.verificationLevel(),
                command.methodology(), command.reagentsUsedNote(), command.findings(), command.interpretation(),
                command.referenceRangeNote(), command.conclusion(), command.machineOutputObjectKey(), null, null));

        List<LabResultParameterValue> values = insertParameterValues(saved.id(), evaluated);
        insertReagents(saved.id(), command.reagents());
        List<CriticalResultNotification> criticalNotifications =
                raiseCriticalNotifications(saved.id(), order.orderedBy(), values, evaluated);

        eventPublisher.publish(LAB_RESULT_RELEASED_EVENT, Map.of(
                "resultId", saved.id().toString(),
                "labOrderId", order.id().toString(),
                "specimenId", specimen.id().toString(),
                "orderedBy", order.orderedBy().toString(),
                "hasCritical", !criticalNotifications.isEmpty()));

        return new RecordLabResultResult(saved, values, criticalNotifications);
    }

    private List<LabResultParameterValue> insertParameterValues(UUID resultId, List<LabResultAssembler.Evaluated> evaluated) {
        List<LabResultParameterValue> values = new ArrayList<>();
        for (LabResultAssembler.Evaluated e : evaluated) {
            values.add(labResultParameterValueRepository.insert(LabResultParameterValue.create(
                    resultId, e.parameterId(), e.resultValue(), e.flag(), e.overridden(), e.overrideReason(), e.appliedRangeId())));
        }
        return values;
    }

    private void insertReagents(UUID resultId, List<ReagentEntry> reagents) {
        if (reagents == null) {
            return;
        }
        for (ReagentEntry reagent : reagents) {
            labResultReagentRepository.insert(com.adags.modules.laboratory.domain.LabResultReagent.create(
                    resultId, reagent.stockBatchId(), reagent.quantityUsed(), reagent.note()));
        }
    }

    // §7: "any parameter flag = C on release -> immediate notification to
    // the ordering doctor." Runs unconditionally, in the same transaction
    // as release — no payment check anywhere in this path (see class comment).
    private List<CriticalResultNotification> raiseCriticalNotifications(
            UUID resultId, UUID orderedBy, List<LabResultParameterValue> values, List<LabResultAssembler.Evaluated> evaluated
    ) {
        List<CriticalResultNotification> notifications = new ArrayList<>();
        for (LabResultParameterValue value : values) {
            if (value.flag() != ResultFlag.CRITICAL) {
                continue;
            }
            CriticalResultNotification notification = criticalResultNotificationRepository.insert(
                    CriticalResultNotification.raise(resultId, value.parameterId(), value.resultValue(), orderedBy));
            notifications.add(notification);
        }
        if (!notifications.isEmpty()) {
            eventPublisher.publish(CRITICAL_LAB_RESULT_RAISED_EVENT, Map.of(
                    "resultId", resultId.toString(),
                    "notifiedUserId", orderedBy.toString(),
                    "criticalCount", notifications.size()));
        }
        return notifications;
    }
}
