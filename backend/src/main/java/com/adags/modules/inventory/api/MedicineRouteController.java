package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.MedicineRouteService;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/medicine-routes/items/{itemId}")
public class MedicineRouteController {
    private final MedicineRouteService service;
    private final AuthorizationService authorization;

    public MedicineRouteController(MedicineRouteService service, AuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping
    public MedicineRouteService.RouteConfiguration current(@PathVariable UUID itemId, @AuthenticationPrincipal Actor actor) {
        if (!authorization.hasAnyPermission(actor, "PRESCRIPTION_CREATE", "DISPENSE_CREATE", "STOCK_READ", "CATALOG_IMPORT")) {
            throw new AccessDeniedException("Medicine route read permission required");
        }
        return service.current(itemId);
    }

    @PostMapping
    public MedicineRouteService.RouteConfiguration configure(@PathVariable UUID itemId, @Valid @RequestBody ConfigureRoutesRequest request,
                                  @AuthenticationPrincipal Actor actor) {
        if (!authorization.hasPermission(actor, "PHARMACY_ITEM_APPROVE")) {
            throw new AccessDeniedException("PHARMACY_ITEM_APPROVE required");
        }
        return service.configure(itemId, request.routes(), request.concentration(), request.reason(), actor.userId());
    }

    public record ConfigureRoutesRequest(@NotEmpty List<String> routes, String concentration,
                                         @NotBlank String reason) {}
}
