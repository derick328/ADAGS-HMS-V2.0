package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.application.MachineOutputStorageService;
import com.adags.modules.laboratory.application.MachineOutputRegistry;
import com.adags.modules.laboratory.domain.*;
import com.adags.shared.authz.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/lab-results/machine-output")
public class MachineOutputController {
    private final MachineOutputStorageService storage;
    private final AuthorizationService authorization;
    private final LabOrderRepository orders;
    private final LabResultRepository results;
    private final com.adags.shared.authz.AuditRecorder audit;
    private final MachineOutputRegistry registry;

    public MachineOutputController(MachineOutputStorageService storage, AuthorizationService authorization,
                                   LabOrderRepository orders, LabResultRepository results, MachineOutputRegistry registry,
                                   com.adags.shared.authz.AuditRecorder audit) {
        this.storage = storage; this.authorization = authorization; this.orders = orders; this.results = results; this.registry = registry; this.audit = audit;
    }

    @PostMapping("/{labOrderId}")
    public UploadMachineOutputResponse upload(@PathVariable UUID labOrderId, @RequestParam("file") MultipartFile file,
                                               @AuthenticationPrincipal Actor actor) {
        LabOrder order = orders.findById(labOrderId).orElseThrow(() -> new NoSuchElementException("Order not found."));
        boolean released = results.findByLabOrderId(order.id()).stream().anyMatch(r -> r.releasedAt() != null);
        require(actor, released ? "LAB_RESULT_AMEND" : "LAB_RESULT_ENTER", order);
        String key = storage.uploadReportPdf(file);
        registry.register(key, order.id(), actor.userId());
        return new UploadMachineOutputResponse(key);
    }

    @GetMapping("/draft/{labOrderId}/{objectKey}")
    public MachineOutputUrlResponse draftUrl(@PathVariable UUID labOrderId, @PathVariable String objectKey,
                                              @AuthenticationPrincipal Actor actor) {
        LabOrder order = orders.findById(labOrderId).orElseThrow(() -> new NoSuchElementException("Order not found."));
        require(actor, "LAB_RESULT_ENTER", order);
        registry.requireOwned(objectKey, order.id(), actor.userId());
        return new MachineOutputUrlResponse(storage.presignedUrl(objectKey));
    }

    @GetMapping("/{resultId}")
    public MachineOutputUrlResponse url(@PathVariable String resultId, @AuthenticationPrincipal Actor actor) {
        LabResult result;
        try {
            result = results.findById(UUID.fromString(resultId))
                    .orElseThrow(() -> new NoSuchElementException("Result not found."));
        } catch (IllegalArgumentException legacyKey) {
            result = results.findByMachineOutputObjectKey(resultId)
                    .orElseThrow(() -> new NoSuchElementException("Machine output not found."));
        }
        LabOrder order = orders.findById(result.labOrderId()).orElseThrow(() -> new NoSuchElementException("Order not found."));
        require(actor, "LAB_RESULT_READ", order);
        if (authorization.hasPermission(actor, "CONSULTATION_CREATE") &&
                authorization.checkPatientAccess(actor, "LAB_RESULT_READ", order.patientId(),
                        ScopeRef.of(ScopeType.FACILITY, order.facilityId())) != PolicyResult.ALLOWED)
            throw new AccessDeniedException("An active care relationship is required.");
        if (result.releasedAt() == null || result.machineOutputObjectKey() == null)
            throw new NoSuchElementException("Machine output not found.");
        audit.record(actor.userId(), "LAB_MACHINE_OUTPUT_READ", "lab_result", result.id(), order.patientId());
        return new MachineOutputUrlResponse(storage.presignedUrl(result.machineOutputObjectKey()));
    }

    private void require(Actor actor, String permission, LabOrder order) {
        if (!authorization.hasPermission(actor, permission, ScopeRef.of(ScopeType.FACILITY, order.facilityId())))
            throw new AccessDeniedException("Missing permission: " + permission);
    }

    public record UploadMachineOutputResponse(String objectKey) {}
    public record MachineOutputUrlResponse(String url) {}
}
