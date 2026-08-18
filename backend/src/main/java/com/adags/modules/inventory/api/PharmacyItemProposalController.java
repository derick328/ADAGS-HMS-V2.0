package com.adags.modules.inventory.api;

import com.adags.modules.inventory.application.ProposeNewItemCommand;
import com.adags.modules.inventory.application.ProposePharmacyItemService;
import com.adags.modules.inventory.application.ProposePriceChangeCommand;
import com.adags.modules.inventory.application.ReviewPharmacyItemProposalCommand;
import com.adags.modules.inventory.application.ReviewPharmacyItemProposalService;
import com.adags.modules.inventory.domain.PharmacyItemProposal;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
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

import java.util.UUID;

// 12-pharmacist.md §9.2: new items and price changes through one approval
// queue. PHARMACY_ITEM_PROPOSE (pharmacist) / PHARMACY_ITEM_APPROVE (admin)
// — both already in the catalogue (V5).
@RestController
@RequestMapping("/api/pharmacy-item-proposals")
public class PharmacyItemProposalController {

    private final ProposePharmacyItemService proposePharmacyItemService;
    private final ReviewPharmacyItemProposalService reviewPharmacyItemProposalService;
    private final AuthorizationService authorizationService;

    public PharmacyItemProposalController(
            ProposePharmacyItemService proposePharmacyItemService,
            ReviewPharmacyItemProposalService reviewPharmacyItemProposalService,
            AuthorizationService authorizationService
    ) {
        this.proposePharmacyItemService = proposePharmacyItemService;
        this.reviewPharmacyItemProposalService = reviewPharmacyItemProposalService;
        this.authorizationService = authorizationService;
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
