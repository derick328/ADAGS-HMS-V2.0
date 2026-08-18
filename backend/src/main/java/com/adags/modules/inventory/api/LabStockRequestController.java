package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.CreateLabStockRequestCommand;
import com.adags.modules.inventory.application.CreateLabStockRequestService;
import com.adags.modules.inventory.application.ReleaseLabStockRequestCommand;
import com.adags.modules.inventory.application.ReleaseLabStockRequestService;
import com.adags.modules.inventory.application.ReviewLabStockRequestService;
import com.adags.modules.inventory.domain.LabStockRequest;
import com.adags.modules.inventory.domain.LabStockRequestRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
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

import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §7/§8: request-and-release, the pattern the ward
// sub-store flow will reuse later. No billing gate — internal movement,
// not a patient-facing dispense.
@RestController
@RequestMapping("/api/lab-stock-requests")
public class LabStockRequestController {

    private final CreateLabStockRequestService createLabStockRequestService;
    private final ReleaseLabStockRequestService releaseLabStockRequestService;
    private final ReviewLabStockRequestService reviewLabStockRequestService;
    private final LabStockRequestRepository labStockRequestRepository;
    private final AuthorizationService authorizationService;

    public LabStockRequestController(
            CreateLabStockRequestService createLabStockRequestService,
            ReleaseLabStockRequestService releaseLabStockRequestService,
            ReviewLabStockRequestService reviewLabStockRequestService,
            LabStockRequestRepository labStockRequestRepository,
            AuthorizationService authorizationService
    ) {
        this.createLabStockRequestService = createLabStockRequestService;
        this.releaseLabStockRequestService = releaseLabStockRequestService;
        this.reviewLabStockRequestService = reviewLabStockRequestService;
        this.labStockRequestRepository = labStockRequestRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LabStockRequestResponse create(@Valid @RequestBody CreateLabStockRequestRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "LAB_STOCK_REQUEST_CREATE");
        LabStockRequest created = createLabStockRequestService.create(new CreateLabStockRequestCommand(
                request.itemId(), request.requestedQuantity(), request.requestingStoreId(), actor.userId()));
        return LabStockRequestResponse.from(created);
    }

    @GetMapping("/{id}")
    public LabStockRequestResponse get(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "STOCK_READ");
        LabStockRequest request = labStockRequestRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lab stock request not found: " + id));
        return LabStockRequestResponse.from(request);
    }

    @PostMapping("/{id}/release")
    public LabStockRequestResponse release(
            @PathVariable UUID id, @Valid @RequestBody ReleaseLabStockRequestRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "LAB_STOCK_REQUEST_REVIEW");
        LabStockRequest released = releaseLabStockRequestService.release(
                new ReleaseLabStockRequestCommand(id, request.batchId(), request.quantity(), actor.userId()));
        return LabStockRequestResponse.from(released);
    }

    @PostMapping("/{id}/reject")
    public LabStockRequestResponse reject(
            @PathVariable UUID id, @Valid @RequestBody RespondToLabStockRequestRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "LAB_STOCK_REQUEST_REVIEW");
        return LabStockRequestResponse.from(
                reviewLabStockRequestService.reject(id, request.responseNotes(), actor.userId()));
    }

    @PostMapping("/{id}/close")
    public LabStockRequestResponse close(
            @PathVariable UUID id, @Valid @RequestBody RespondToLabStockRequestRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "LAB_STOCK_REQUEST_REVIEW");
        return LabStockRequestResponse.from(
                reviewLabStockRequestService.close(id, request.responseNotes(), actor.userId()));
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
