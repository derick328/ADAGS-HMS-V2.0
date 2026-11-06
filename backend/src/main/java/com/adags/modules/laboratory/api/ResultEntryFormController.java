package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.LabReferenceRange;
import com.adags.modules.laboratory.domain.LabReferenceRangeRepository;
import com.adags.modules.laboratory.domain.LabReferenceRangeResolver;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestParameter;
import com.adags.modules.laboratory.domain.LabTestParameterRepository;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.Sex;
import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenRepository;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.PatientDemographics;
import com.adags.shared.kernel.PatientIdentity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

// 15-lab-technician.md §5.1/§5.2: read-only GET behind Result Entry —
// generates the form from LabTestParameter, resolving each parameter's
// reference range exactly the way RecordLabResultService/
// LabReferenceRangeResolver do at submit time, just without persisting
// anything. Keeps range resolution in one place (the resolver) rather than
// duplicating age/sex-band logic in the frontend.
@RestController
@RequestMapping("/api/lab-orders")
public class ResultEntryFormController {

    private final LabOrderRepository labOrderRepository;
    private final LabTestRepository labTestRepository;
    private final LabTestParameterRepository labTestParameterRepository;
    private final LabReferenceRangeRepository labReferenceRangeRepository;
    private final SpecimenRepository specimenRepository;
    private final com.adags.modules.laboratory.domain.LabResultRepository labResultRepository;
    private final PatientFacade patientFacade;
    private final AuthorizationService authorizationService;

    public ResultEntryFormController(
            LabOrderRepository labOrderRepository,
            LabTestRepository labTestRepository,
            LabTestParameterRepository labTestParameterRepository,
            LabReferenceRangeRepository labReferenceRangeRepository,
            SpecimenRepository specimenRepository,
            com.adags.modules.laboratory.domain.LabResultRepository labResultRepository,
            PatientFacade patientFacade,
            AuthorizationService authorizationService
    ) {
        this.labOrderRepository = labOrderRepository;
        this.labTestRepository = labTestRepository;
        this.labTestParameterRepository = labTestParameterRepository;
        this.labReferenceRangeRepository = labReferenceRangeRepository;
        this.specimenRepository = specimenRepository;
        this.labResultRepository = labResultRepository;
        this.patientFacade = patientFacade;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{id}/result-entry-form")
    public ResultEntryFormResponse resultEntryForm(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        LabOrder order = labOrderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + id));
        if (!authorizationService.hasPermission(
                actor, "LAB_RESULT_ENTER", ScopeRef.of(ScopeType.FACILITY, order.facilityId()))) {
            throw new AccessDeniedException("Missing permission: LAB_RESULT_ENTER");
        }

        LabTest test = labTestRepository.findById(order.labTestId())
                .orElseThrow(() -> new NoSuchElementException("Lab test not found: " + order.labTestId()));
        Specimen specimen = specimenRepository.findByLabOrderId(order.id()).stream()
                .filter(s -> s.status() == com.adags.modules.laboratory.domain.SpecimenStatus.IN_PROGRESS).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No specimen accepted for order: " + id));

        PatientIdentity identity = patientFacade.findIdentity(order.patientId()).orElse(null);
        Optional<PatientDemographics> demographics = patientFacade.findDemographics(order.patientId());
        Integer ageYears = demographics.map(d -> Period.between(d.dateOfBirth(), LocalDate.now()).getYears()).orElse(null);
        Sex sex = demographics.map(d -> Sex.fromPatientGender(d.gender())).orElse(Sex.ANY);
        int ageMonths = demographics
                .map(d -> LabReferenceRangeResolver.ageInMonths(d.dateOfBirth(), LocalDate.now()))
                .orElse(0);

        List<LabTestParameter> parameters = labTestParameterRepository.findByTestId(test.id());
        List<ResultEntryFormResponse.ParameterFieldResponse> fields = parameters.stream()
                .map(parameter -> toFieldResponse(parameter, ageMonths, sex))
                .toList();

        return new ResultEntryFormResponse(
                order.id(), specimen.id(), specimen.accessionNo(), specimen.quality(), specimen.qualityNote(),
                identity == null ? null : identity.fullName(), identity == null ? null : identity.hospitalNumber(),
                ageYears, sex.name(), test.code(), test.name(), order.clinicalIndication(),
                order.specialInstructions(), order.urgency().name(),
                labResultRepository.findByLabOrderId(order.id()).stream().anyMatch(r -> r.releasedAt() != null)
                        ? "RELEASED" : specimen.status().name(),
                specimen.sampleType(), specimen.collectedAt(),
                authorizationService.hasPermission(actor, "PATIENT_READ_DEMOGRAPHIC",
                        ScopeRef.of(ScopeType.FACILITY, order.facilityId()))
                        ? patientFacade.findNationalId(order.patientId()).orElse(null) : null,
                test.producesMachineOutput(), fields);
    }

    private ResultEntryFormResponse.ParameterFieldResponse toFieldResponse(LabTestParameter parameter, int ageMonths, Sex sex) {
        List<LabReferenceRange> candidates = labReferenceRangeRepository.findCurrentByParameterId(parameter.id());
        Optional<LabReferenceRange> range = LabReferenceRangeResolver.resolve(candidates, ageMonths, sex);
        return new ResultEntryFormResponse.ParameterFieldResponse(
                parameter.id(), parameter.code(), parameter.name(), parameter.unit(), parameter.sortOrder(),
                parameter.method(),
                range.map(LabReferenceRange::normalLow).orElse(null),
                range.map(LabReferenceRange::normalHigh).orElse(null),
                range.map(LabReferenceRange::criticalLow).orElse(null),
                range.map(LabReferenceRange::criticalHigh).orElse(null));
    }
}
