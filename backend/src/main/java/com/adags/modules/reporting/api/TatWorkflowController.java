package com.adags.modules.reporting.api;

import com.adags.modules.reporting.application.TatWorkflowService;
import com.adags.shared.authz.Actor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
public class TatWorkflowController {
    private final TatWorkflowService service;

    public TatWorkflowController(TatWorkflowService service) { this.service = service; }

    @PostMapping("/api/encounters/{id}/triage-start")
    public StartResponse startTriage(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        return new StartResponse(service.startTriage(id, actor));
    }

    @PostMapping("/api/prescription-lines/{id}/processing-start")
    public StartResponse startPharmacy(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        return new StartResponse(service.startPharmacy(id, actor));
    }

    public record StartResponse(Instant startedAt) {}
}
