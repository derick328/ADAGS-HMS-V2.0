package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.application.AmendLabResultCommand;
import com.adags.modules.laboratory.application.AmendLabResultService;
import com.adags.modules.laboratory.application.ParameterEntry;
import com.adags.modules.laboratory.application.ReagentEntry;
import com.adags.modules.laboratory.application.RecordLabResultCommand;
import com.adags.modules.laboratory.application.RecordLabResultResult;
import com.adags.modules.laboratory.application.RecordLabResultService;
import com.adags.modules.laboratory.application.VerifyLabResultCommand;
import com.adags.modules.laboratory.application.VerifyLabResultService;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.LabReferenceRange;
import com.adags.modules.laboratory.domain.LabReferenceRangeRepository;
import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultParameterValue;
import com.adags.modules.laboratory.domain.LabResultParameterValueRepository;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestParameter;
import com.adags.modules.laboratory.domain.LabTestParameterRepository;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenRepository;
import com.adags.modules.identity.IdentityFacade;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.shared.kernel.PatientIdentity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

// 15-lab-technician.md §5-§6, 16-lab-verification-model.md v0.2 §2/§8.
// Scoped at FACILITY, same granularity as LabOrderController/SpecimenController.
@RestController
@RequestMapping("/api/lab-results")
public class LabResultController {

    private final RecordLabResultService recordLabResultService;
    private final AmendLabResultService amendLabResultService;
    private final VerifyLabResultService verifyLabResultService;
    private final SpecimenRepository specimenRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabResultRepository labResultRepository;
    private final LabResultParameterValueRepository labResultParameterValueRepository;
    private final LabTestRepository labTestRepository;
    private final LabTestParameterRepository labTestParameterRepository;
    private final LabReferenceRangeRepository labReferenceRangeRepository;
    private final PatientFacade patientFacade;
    private final IdentityFacade identityFacade;
    private final AuthorizationService authorizationService;

    public LabResultController(
            RecordLabResultService recordLabResultService,
            AmendLabResultService amendLabResultService,
            VerifyLabResultService verifyLabResultService,
            SpecimenRepository specimenRepository,
            LabOrderRepository labOrderRepository,
            LabResultRepository labResultRepository,
            LabResultParameterValueRepository labResultParameterValueRepository,
            LabTestRepository labTestRepository,
            LabTestParameterRepository labTestParameterRepository,
            LabReferenceRangeRepository labReferenceRangeRepository,
            PatientFacade patientFacade,
            IdentityFacade identityFacade,
            AuthorizationService authorizationService
    ) {
        this.recordLabResultService = recordLabResultService;
        this.amendLabResultService = amendLabResultService;
        this.verifyLabResultService = verifyLabResultService;
        this.specimenRepository = specimenRepository;
        this.labOrderRepository = labOrderRepository;
        this.labResultRepository = labResultRepository;
        this.labResultParameterValueRepository = labResultParameterValueRepository;
        this.labTestRepository = labTestRepository;
        this.labTestParameterRepository = labTestParameterRepository;
        this.labReferenceRangeRepository = labReferenceRangeRepository;
        this.patientFacade = patientFacade;
        this.identityFacade = identityFacade;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LabResultResponse record(@Valid @RequestBody RecordLabResultRequest request, @AuthenticationPrincipal Actor actor) {
        Specimen specimen = specimenRepository.findById(request.specimenId())
                .orElseThrow(() -> new NoSuchElementException("Specimen not found: " + request.specimenId()));
        LabOrder order = labOrderRepository.findById(specimen.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + specimen.labOrderId()));
        requirePermission(actor, "LAB_RESULT_ENTER", order.facilityId());

        RecordLabResultResult result = recordLabResultService.record(new RecordLabResultCommand(
                request.specimenId(), actor.userId(), request.methodology(), request.reagentsUsedNote(),
                request.findings(), request.interpretation(), request.referenceRangeNote(), request.conclusion(),
                request.machineOutputObjectKey(), toParameterEntries(request.parameterValues()), toReagentEntries(request.reagents())));
        return LabResultResponse.from(result);
    }

    @PostMapping("/{id}/amend")
    public LabResultResponse amend(
            @PathVariable UUID id, @Valid @RequestBody AmendLabResultRequest request, @AuthenticationPrincipal Actor actor
    ) {
        LabResult original = labResultRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lab result not found: " + id));
        LabOrder order = labOrderRepository.findById(original.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + original.labOrderId()));
        requirePermission(actor, "LAB_RESULT_AMEND", order.facilityId());

        RecordLabResultResult result = amendLabResultService.amend(new AmendLabResultCommand(
                id, actor.userId(), request.amendmentReason(), request.methodology(), request.reagentsUsedNote(),
                request.findings(), request.interpretation(), request.referenceRangeNote(), request.conclusion(),
                request.machineOutputObjectKey(), toParameterEntries(request.parameterValues()), toReagentEntries(request.reagents())));
        return LabResultResponse.from(result);
    }

    @PostMapping("/{id}/verify")
    public void verify(
            @PathVariable UUID id, @RequestBody(required = false) VerifyLabResultRequest request, @AuthenticationPrincipal Actor actor
    ) {
        LabResult result = labResultRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lab result not found: " + id));
        LabOrder order = labOrderRepository.findById(result.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + result.labOrderId()));
        requirePermission(actor, "LAB_RESULT_VERIFY", order.facilityId());

        String comment = request == null ? null : request.verificationComment();
        verifyLabResultService.verify(new VerifyLabResultCommand(id, actor.userId(), comment));
    }

    // 15-lab-technician.md §9: the printable-report / amendment-supersession
    // detail view. Also what an amended result's "supersedes" line reads
    // its original from (amendsId -> another call to this same endpoint).
    @GetMapping("/{id}")
    public LabResultDetailResponse get(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        LabResult result = labResultRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lab result not found: " + id));
        LabOrder order = labOrderRepository.findById(result.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + result.labOrderId()));
        requirePermission(actor, "LAB_RESULT_READ", order.facilityId());
        return toDetailResponse(result, order);
    }

    // 08-doctor-navigation.md §5: doctor's Request History detail — every
    // result the lab tech released against one order (an amendment
    // appends, so multiple rows are possible; newest first). Gated on
    // LAB_RESULT_READ, which DOCTOR already holds; facility-scoped
    // through the order the same way get() above is.
    @GetMapping("/by-lab-order/{labOrderId}")
    public List<LabResultDetailResponse> byLabOrder(@PathVariable UUID labOrderId, @AuthenticationPrincipal Actor actor) {
        LabOrder order = labOrderRepository.findById(labOrderId)
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + labOrderId));
        requirePermission(actor, "LAB_RESULT_READ", order.facilityId());
        return labResultRepository.findByLabOrderId(labOrderId).stream()
                .map(r -> toDetailResponse(r, order))
                .toList();
    }

    // §9: "My results" — this technician's own completed work.
    @GetMapping("/mine")
    public List<LabResultDetailResponse> mine(@AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "LAB_RESULT_READ")) {
            throw new AccessDeniedException("Missing permission: LAB_RESULT_READ");
        }
        return labResultRepository.findByEnteredBy(actor.userId()).stream()
                .map(r -> toDetailResponse(r, requireOrder(r)))
                .toList();
    }

    // §9: "Patient lookup" — by patient or accession, every result
    // regardless of technician. At least one of the two params is required.
    @GetMapping("/search")
    public List<LabResultDetailResponse> search(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) String accessionNo,
            @AuthenticationPrincipal Actor actor
    ) {
        if (!authorizationService.hasPermission(actor, "LAB_RESULT_READ")
                || !authorizationService.hasPermission(actor, "PATIENT_READ_DEMOGRAPHIC")) {
            throw new AccessDeniedException("Missing permission: LAB_RESULT_READ");
        }
        if ((patientId == null) && (accessionNo == null || accessionNo.isBlank())) {
            throw new IllegalArgumentException("Provide a patientId or an accessionNo to search.");
        }
        List<LabResult> results = accessionNo != null && !accessionNo.isBlank()
                ? labResultRepository.findByAccessionNo(accessionNo)
                : labResultRepository.findByPatientId(patientId);
        return results.stream().map(r -> toDetailResponse(r, requireOrder(r))).toList();
    }

    private LabOrder requireOrder(LabResult result) {
        return labOrderRepository.findById(result.labOrderId())
                .orElseThrow(() -> new NoSuchElementException("Lab order not found: " + result.labOrderId()));
    }

    private LabResultDetailResponse toDetailResponse(LabResult result, LabOrder order) {
        LabTest test = labTestRepository.findById(order.labTestId()).orElse(null);
        Specimen specimen = specimenRepository.findById(result.specimenId()).orElse(null);
        PatientIdentity identity = patientFacade.findIdentity(order.patientId()).orElse(null);
        String enteredByName = identityFacade.findUsername(result.enteredBy()).orElse(null);
        String verifiedByName = result.verifiedBy() == null
                ? null : identityFacade.findUsername(result.verifiedBy()).orElse(null);
        Instant amendsOriginalEnteredAt = result.amendsId() == null
                ? null : labResultRepository.findById(result.amendsId()).map(LabResult::enteredAt).orElse(null);

        List<LabTestParameter> parameterDefinitions = test == null
                ? List.of() : labTestParameterRepository.findByTestId(test.id());
        List<LabResultDetailResponse.DetailParameterValueResponse> parameterValues =
                labResultParameterValueRepository.findByResultId(result.id()).stream()
                        .map(value -> toDetailParameterValue(value, parameterDefinitions))
                        .toList();

        return new LabResultDetailResponse(
                result.id(), result.labOrderId(), result.specimenId(),
                specimen == null ? null : specimen.accessionNo(), specimen == null ? null : specimen.sampleType(),
                identity == null ? null : identity.fullName(), identity == null ? null : identity.hospitalNumber(),
                test == null ? null : test.code(), test == null ? "Unknown test" : test.name(),
                order.clinicalIndication(),
                result.methodology(), result.reagentsUsedNote(), result.findings(), result.interpretation(),
                result.referenceRangeNote(), result.conclusion(), result.machineOutputObjectKey(),
                enteredByName, result.enteredAt(), result.releasedAt(),
                result.verificationState(), result.verificationLevelApplied(), verifiedByName, result.verifiedAt(),
                result.amendsId(), amendsOriginalEnteredAt, result.amendmentReason(), parameterValues);
    }

    private LabResultDetailResponse.DetailParameterValueResponse toDetailParameterValue(
            LabResultParameterValue value, List<LabTestParameter> parameterDefinitions
    ) {
        LabTestParameter definition = parameterDefinitions.stream()
                .filter(p -> p.id().equals(value.parameterId())).findFirst().orElse(null);
        LabReferenceRange range = value.appliedRangeId() == null
                ? null : labReferenceRangeRepository.findById(value.appliedRangeId()).orElse(null);
        return new LabResultDetailResponse.DetailParameterValueResponse(
                value.parameterId(), definition == null ? null : definition.name(),
                definition == null ? null : definition.unit(), definition == null ? null : definition.method(),
                value.resultValue(), value.flag().name(), value.flagOverridden(), value.overrideReason(),
                range == null ? null : range.normalLow(), range == null ? null : range.normalHigh(),
                range == null ? null : range.criticalLow(), range == null ? null : range.criticalHigh());
    }

    private List<ParameterEntry> toParameterEntries(List<ParameterValueRequest> requests) {
        return requests.stream()
                .map(r -> new ParameterEntry(r.parameterId(), r.resultValue(), r.overrideFlag(), r.overrideReason()))
                .toList();
    }

    private List<ReagentEntry> toReagentEntries(List<ReagentRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream().map(r -> new ReagentEntry(r.stockBatchId(), r.quantityUsed(), r.note())).toList();
    }

    private void requirePermission(Actor actor, String permissionCode, UUID facilityId) {
        if (!authorizationService.hasPermission(actor, permissionCode, ScopeRef.of(ScopeType.FACILITY, facilityId))) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
