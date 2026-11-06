package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.application.LabResultDraftService;
import com.adags.shared.authz.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/lab-orders/{labOrderId}/result-draft")
public class LabResultDraftController {
    private final LabResultDraftService service;

    public LabResultDraftController(LabResultDraftService service) { this.service = service; }

    @GetMapping
    public DraftResponse get(@PathVariable UUID labOrderId, @AuthenticationPrincipal Actor actor) {
        return service.get(labOrderId, actor);
    }

    @PutMapping
    public DraftResponse save(@PathVariable UUID labOrderId, @RequestBody SaveDraftRequest request,
                              @AuthenticationPrincipal Actor actor) {
        return service.save(labOrderId, request, actor);
    }

    @PostMapping("/submit")
    public LabResultResponse submit(@PathVariable UUID labOrderId, @Valid @RequestBody SubmitDraftRequest request,
                                    @AuthenticationPrincipal Actor actor) {
        return service.submit(labOrderId, request, actor);
    }

    public record SaveDraftRequest(Long version, JsonNode payload) {}
    public record SubmitDraftRequest(Long version, @Valid RecordLabResultRequest result) {}
    public record DraftResponse(UUID id, UUID specimenId, UUID ownerId, JsonNode payload, long version,
                                String status, UUID submittedResultId, Instant createdAt, Instant updatedAt) {}
}
