package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.ProposeNewItemCommand;
import com.adags.modules.inventory.application.ProposePharmacyItemService;
import com.adags.modules.inventory.application.ProposePriceChangeCommand;
import com.adags.modules.inventory.application.ReviewPharmacyItemProposalCommand;
import com.adags.modules.inventory.application.ReviewPharmacyItemProposalService;
import com.adags.modules.inventory.domain.PharmacyItemProposal;
import com.adags.modules.inventory.domain.PharmacyItemProposalRepository;
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

import java.util.List;
import java.util.UUID;

// 12-pharmacist.md §9.2: new items and price changes through one approval
// queue. PHARMACY_ITEM_PROPOSE (pharmacist) / PHARMACY_ITEM_APPROVE (admin)
// — both already in the catalogue (V5).
@RestController
@RequestMapping("/api/pharmacy-item-proposals")
public class PharmacyItemProposalController {

    private final ProposePharmacyItemService proposePharmacyItemService;
    private final ReviewPharmacyItemProposalService reviewPharmacyItemProposalService;
    private final PharmacyItemProposalRepository pharmacyItemProposalRepository;
    private final AuthorizationService authorizationService;

    public PharmacyItemProposalController(
            ProposePharmacyItemService proposePharmacyItemService,
            ReviewPharmacyItemProposalService reviewPharmacyItemProposalService,
            PharmacyItemProposalRepository pharmacyItemProposalRepository,
            AuthorizationService authorizationService
    ) {
        this.proposePharmacyItemService = proposePharmacyItemService;
        this.reviewPharmacyItemProposalService = reviewPharmacyItemProposalService;
        this.pharmacyItemProposalRepository = pharmacyItemProposalRepository;
        this.authorizationService = authorizationService;
    }

    // 18-admin.md §2/§7: the admin's "Pricing / price proposals" review
    // queue — every PROPOSED new-item and price-change request awaiting a
    // decision, oldest first.
    @GetMapping("/pending")
    public List<PharmacyItemProposalResponse> pending(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "PHARMACY_ITEM_APPROVE");
        return pharmacyItemProposalRepository.findPending().stream().map(PharmacyItemProposalResponse::from).toList();
    }

    @PostMapping("/new-item")
    @ResponseStatus(HttpStatus.CREATED)
    public PharmacyItemProposalResponse proposeNewItem(@Valid @RequestBody ProposeNewItemRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "PHARMACY_ITEM_PROPOSE");
        PharmacyItemProposal proposal = proposePharmacyItemService.proposeNewItem(new ProposeNewItemCommand(
                request.proposedName(), request.proposedClassification(), request.proposedForm(),
                request.proposedUnit(), request.proposedPriceMinor(), request.justification(), actor.userId()));
        return PharmacyItemProposalResponse.from(proposal);
    }

    @PostMapping("/price-change")
    @ResponseStatus(HttpStatus.CREATED)
    public PharmacyItemProposalResponse proposePriceChange(@Valid @RequestBody ProposePriceChangeRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "PHARMACY_ITEM_PROPOSE");
        PharmacyItemProposal proposal = proposePharmacyItemService.proposePriceChange(new ProposePriceChangeCommand(
                request.itemId(), request.proposedPriceMinor(), request.justification(), actor.userId()));
        return PharmacyItemProposalResponse.from(proposal);
    }

    @PostMapping("/form-change")
    @ResponseStatus(HttpStatus.CREATED)
    public PharmacyItemProposalResponse proposeFormChange(@Valid @RequestBody ProposeFormChangeRequest request,
                                                            @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "PHARMACY_ITEM_PROPOSE");
        return PharmacyItemProposalResponse.from(proposePharmacyItemService.proposeFormChange(
                request.itemId(), request.proposedForm(), request.proposedUnit(),
                request.justification(), actor.userId()));
    }

    @PostMapping("/{id}/review")
    public PharmacyItemProposalResponse review(
            @PathVariable UUID id, @Valid @RequestBody ReviewPharmacyItemProposalRequest request, @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "PHARMACY_ITEM_APPROVE");
        PharmacyItemProposal proposal = reviewPharmacyItemProposalService.review(
                new ReviewPharmacyItemProposalCommand(id, request.decision(), actor.userId(), request.reviewNote()));
        return PharmacyItemProposalResponse.from(proposal);
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
