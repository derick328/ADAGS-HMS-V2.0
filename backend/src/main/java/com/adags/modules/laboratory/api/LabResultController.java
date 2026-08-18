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
import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.modules.laboratory.domain.Specimen;
import com.adags.modules.laboratory.domain.SpecimenRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    private final AuthorizationService authorizationService;

    public LabResultController(
            RecordLabResultService recordLabResultService,
            AmendLabResultService amendLabResultService,
            VerifyLabResultService verifyLabResultService,
            SpecimenRepository specimenRepository,
            LabOrderRepository labOrderRepository,
            LabResultRepository labResultRepository,
            AuthorizationService authorizationService
    ) {
        this.recordLabResultService = recordLabResultService;
        this.amendLabResultService = amendLabResultService;
        this.verifyLabResultService = verifyLabResultService;
        this.specimenRepository = specimenRepository;
        this.labOrderRepository = labOrderRepository;
        this.labResultRepository = labResultRepository;
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
