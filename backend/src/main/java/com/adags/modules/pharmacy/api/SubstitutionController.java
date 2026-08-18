package com.adags.modules.pharmacy.api;

import com.adags.modules.pharmacy.application.ProposeSubstitutionCommand;
import com.adags.modules.pharmacy.application.ProposeSubstitutionService;
import com.adags.modules.pharmacy.application.ReviewSubstitutionCommand;
import com.adags.modules.pharmacy.application.ReviewSubstitutionService;
import com.adags.modules.pharmacy.application.SubstitutionReviewResult;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.modules.pharmacy.domain.SubstitutionProposal;
import com.adags.modules.pharmacy.domain.SubstitutionProposalRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §5.1: pharmacist proposes (SUBSTITUTION_PROPOSE), doctor
// reviews. No dedicated review permission exists in the catalogue —
// PRESCRIPTION_CREATE (doctors only, per D1) is the closest existing fit.
@RestController
public class SubstitutionController {

    private final ProposeSubstitutionService proposeSubstitutionService;
    private final ReviewSubstitutionService reviewSubstitutionService;
    private final PrescriptionLineRepository prescriptionLineRepository;
    private final PrescriptionOrderRepository prescriptionOrderRepository;
    private final SubstitutionProposalRepository substitutionProposalRepository;
    private final AuthorizationService authorizationService;

    public SubstitutionController(
            ProposeSubstitutionService proposeSubstitutionService,
            ReviewSubstitutionService reviewSubstitutionService,
            PrescriptionLineRepository prescriptionLineRepository,
            PrescriptionOrderRepository prescriptionOrderRepository,
            SubstitutionProposalRepository substitutionProposalRepository,
            AuthorizationService authorizationService
    ) {
        this.proposeSubstitutionService = proposeSubstitutionService;
        this.reviewSubstitutionService = reviewSubstitutionService;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.substitutionProposalRepository = substitutionProposalRepository;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/api/prescription-lines/{id}/substitution-proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public SubstitutionProposalResponse propose(
            @PathVariable UUID id, @Valid @RequestBody ProposeSubstitutionRequest request, @AuthenticationPrincipal Actor actor
    ) {
        UUID facilityId = facilityIdForLine(id);
        if (!authorizationService.hasPermission(actor, "SUBSTITUTION_PROPOSE", ScopeRef.of(ScopeType.FACILITY, facilityId))) {
            throw new AccessDeniedException("Missing permission: SUBSTITUTION_PROPOSE");
        }

        SubstitutionProposal proposal = proposeSubstitutionService.propose(
                new ProposeSubstitutionCommand(id, request.proposedItemId(), request.reason(), actor.userId()));
        return SubstitutionProposalResponse.from(proposal);
    }

    @PostMapping("/api/substitution-proposals/{id}/review")
    public SubstitutionReviewResponse review(
            @PathVariable UUID id, @Valid @RequestBody ReviewSubstitutionRequest request, @AuthenticationPrincipal Actor actor
    ) {
        SubstitutionProposal proposal = substitutionProposalRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Substitution proposal not found: " + id));
        UUID facilityId = facilityIdForLine(proposal.prescriptionLineId());
        if (!authorizationService.hasPermission(actor, "PRESCRIPTION_CREATE", ScopeRef.of(ScopeType.FACILITY, facilityId))) {
            throw new AccessDeniedException("Missing permission: PRESCRIPTION_CREATE");
        }

        SubstitutionReviewResult result = reviewSubstitutionService.review(
                new ReviewSubstitutionCommand(id, request.decision(), actor.userId(), request.reviewNote()));
        return SubstitutionReviewResponse.from(result);
    }

    private UUID facilityIdForLine(UUID prescriptionLineId) {
        PrescriptionLine line = prescriptionLineRepository.findById(prescriptionLineId)
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + prescriptionLineId));
        PrescriptionOrder order = prescriptionOrderRepository.findById(line.prescriptionOrderId())
                .orElseThrow(() -> new NoSuchElementException("Prescription order not found: " + line.prescriptionOrderId()));
        return order.facilityId();
    }
}
