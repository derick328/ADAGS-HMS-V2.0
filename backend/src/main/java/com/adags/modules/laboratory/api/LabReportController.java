package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.application.LabReportRegistry;
import com.adags.modules.laboratory.application.MachineOutputStorageService;
import com.adags.modules.laboratory.domain.LabOrder;
import com.adags.modules.laboratory.domain.LabOrderRepository;
import com.adags.modules.laboratory.domain.LabResult;
import com.adags.modules.laboratory.domain.LabResultRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.PolicyResult;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/lab-results/report")
public class LabReportController {
    private final LabOrderRepository orders;
    private final LabResultRepository results;
    private final com.adags.shared.authz.AuditRecorder audit;
    private final AuthorizationService authorization;
    private final MachineOutputStorageService storage;
    private final LabReportRegistry registry;

    public LabReportController(LabOrderRepository orders, LabResultRepository results, AuthorizationService authorization,
                               MachineOutputStorageService storage, LabReportRegistry registry,
                               com.adags.shared.authz.AuditRecorder audit) {
        this.orders = orders; this.results = results; this.authorization = authorization;
        this.storage = storage; this.registry = registry; this.audit = audit;
    }

    @PostMapping("/{labOrderId}")
    public UploadMachineOutputResponse upload(@PathVariable UUID labOrderId, @RequestParam MultipartFile file,
                                               @AuthenticationPrincipal Actor actor) {
        LabOrder order = orders.findById(labOrderId).orElseThrow(() -> new NoSuchElementException("Order not found."));
        boolean released = results.findByLabOrderId(order.id()).stream().anyMatch(r -> r.releasedAt() != null);
        require(actor, released ? "LAB_RESULT_AMEND" : "LAB_RESULT_ENTER", order);
        String key = storage.uploadReportPdf(file);
        registry.register(key, order.id(), actor.userId());
        return new UploadMachineOutputResponse(key);
    }

    @GetMapping("/{resultId}/url")
    public MachineOutputUrlResponse url(@PathVariable UUID resultId, @AuthenticationPrincipal Actor actor) {
        LabResult result = results.findById(resultId).orElseThrow(() -> new NoSuchElementException("Result not found."));
        LabOrder order = orders.findById(result.labOrderId()).orElseThrow(() -> new NoSuchElementException("Order not found."));
        require(actor, "LAB_RESULT_READ", order);
        if (authorization.hasPermission(actor, "CONSULTATION_CREATE") &&
                authorization.checkPatientAccess(actor, "LAB_RESULT_READ", order.patientId(),
                        ScopeRef.of(ScopeType.FACILITY, order.facilityId())) != PolicyResult.ALLOWED)
            throw new AccessDeniedException("An active care relationship is required.");
        if (result.releasedAt() == null || result.labReportObjectKey() == null)
            throw new NoSuchElementException("Report attachment not found.");
        audit.record(actor.userId(), "LAB_REPORT_PDF_READ", "lab_result", result.id(), order.patientId());
        return new MachineOutputUrlResponse(storage.presignedUrl(result.labReportObjectKey()));
    }

    private void require(Actor actor, String permission, LabOrder order) {
        if (!authorization.hasPermission(actor, permission, ScopeRef.of(ScopeType.FACILITY, order.facilityId())))
            throw new AccessDeniedException("Missing permission: " + permission);
    }

    public record UploadMachineOutputResponse(String objectKey) {}
    public record MachineOutputUrlResponse(String url) {}
}
