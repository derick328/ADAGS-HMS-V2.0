package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.MedicinePackagingService;
import com.adags.modules.inventory.application.MedicinePackagingService.Packaging;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/medicine-packaging")
public class MedicinePackagingController {
    private final MedicinePackagingService service;
    private final AuthorizationService authorization;

    public MedicinePackagingController(MedicinePackagingService service, AuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping("/items/{itemId}")
    public List<Packaging> forItem(@PathVariable UUID itemId, @AuthenticationPrincipal Actor actor) {
        if (!authorization.hasAnyPermission(actor, "STOCK_READ", "PRESCRIPTION_CREATE", "DISPENSE_CREATE")) {
            throw new AccessDeniedException("Packaging read permission required");
        }
        return service.approvedForItem(itemId);
    }

    @GetMapping("/pending")
    public List<Packaging> pending(@AuthenticationPrincipal Actor actor) {
        if (!authorization.hasPermission(actor, "PACKAGING_APPROVE")) {
            throw new AccessDeniedException("PACKAGING_APPROVE required");
        }
        return service.pending();
    }

    @PostMapping("/items/{itemId}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public Packaging propose(@PathVariable UUID itemId, @Valid @RequestBody ProposalRequest request,
                             @AuthenticationPrincipal Actor actor) {
        if (!authorization.hasPermission(actor, "PACKAGING_PROPOSE")) {
            throw new AccessDeniedException("PACKAGING_PROPOSE required");
        }
        return service.propose(itemId, request.unit(), request.quantityInBaseUnits(), request.canSplit(),
                request.reason(), actor.userId());
    }

    @PostMapping("/{id}/approve")
    public Packaging approve(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        if (!authorization.hasPermission(actor, "PACKAGING_APPROVE")) {
            throw new AccessDeniedException("PACKAGING_APPROVE required");
        }
        return service.approve(id, actor.userId());
    }

    public record ProposalRequest(@NotBlank String unit, @Min(2) int quantityInBaseUnits,
                                  boolean canSplit, @NotBlank String reason) {
    }
}
