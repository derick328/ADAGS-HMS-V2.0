package com.adags.modules.laboratory.api;

import com.adags.modules.laboratory.application.ProposeLabItemCommand;
import com.adags.modules.laboratory.application.ProposeLabItemService;
import com.adags.modules.laboratory.domain.LabItemProposal;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 15-lab-technician.md §10: Lab Catalogue proposals — submit-only in this
// phase, same as the pharmacist's proposal screen has no "my proposals"
// list either. Admin review is a separate, later build against this same
// table (V62).
@RestController
@RequestMapping("/api/lab-item-proposals")
public class LabItemProposalController {

    private final ProposeLabItemService proposeLabItemService;
    private final AuthorizationService authorizationService;

    public LabItemProposalController(ProposeLabItemService proposeLabItemService, AuthorizationService authorizationService) {
        this.proposeLabItemService = proposeLabItemService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LabItemProposalResponse propose(@Valid @RequestBody ProposeLabItemRequest request, @AuthenticationPrincipal Actor actor) {
        if (!authorizationService.hasPermission(actor, "LAB_ITEM_PROPOSE")) {
            throw new AccessDeniedException("Missing permission: LAB_ITEM_PROPOSE");
        }
        List<ProposeLabItemCommand.ProposedParameter> parameters = request.parameters() == null
                ? List.of()
                : request.parameters().stream()
                        .map(p -> new ProposeLabItemCommand.ProposedParameter(p.name(), p.unit(), p.method()))
                        .toList();
        LabItemProposal proposal = proposeLabItemService.propose(new ProposeLabItemCommand(
                request.proposedName(), request.proposedClassification(), request.proposedPriceMinor(),
                request.notes(), request.specimenType(), request.containerType(), request.expectedTatHours(),
                request.justification(), actor.userId(), parameters));
        return LabItemProposalResponse.from(proposal);
    }
}
