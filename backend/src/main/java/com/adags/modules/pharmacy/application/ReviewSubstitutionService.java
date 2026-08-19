package com.adags.modules.pharmacy.application;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.inventory.InventoryFacade;
import com.adags.modules.pharmacy.domain.PrescriptionLine;
import com.adags.modules.pharmacy.domain.PrescriptionLineRepository;
import com.adags.modules.pharmacy.domain.PrescriptionOrder;
import com.adags.modules.pharmacy.domain.PrescriptionOrderRepository;
import com.adags.modules.pharmacy.domain.SubstitutionAlreadyReviewedException;
import com.adags.modules.pharmacy.domain.SubstitutionProposal;
import com.adags.modules.pharmacy.domain.SubstitutionProposalRepository;
import com.adags.modules.pharmacy.domain.SubstitutionSelfReviewNotAllowedException;
import com.adags.modules.pharmacy.domain.SubstitutionStatus;
import com.adags.shared.kernel.EncounterContext;
import com.adags.shared.kernel.ItemSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

// 12-pharmacist.md §5.1: "On approval: original line cancelled, replacement
// line posted. Price difference -> top-up owed, or partial refund via
// accountant." The top-up case resolves itself structurally: the original
// unpaid charge is voided and a fresh charge is posted for the
// replacement's own price — the patient simply owes (or owes less for)
// the new line, no separate reconciliation code needed. The refund case
// cannot be automated here: InvoiceRepository.voidUnpaid refuses once
// anything has settled, and this codebase has no refund service behind
// PAYMENT_REFUND yet — that half is left to the accountant, same
// documented gap as MarkUnavailableService's "refund the line through the
// accountant path".
@Service
public class ReviewSubstitutionService {

    private static final String SUBSTITUTION_APPROVED_EVENT = "SubstitutionApproved";

    private final SubstitutionProposalRepository substitutionProposalRepository;
    private final PrescriptionLineRepository prescriptionLineRepository;
    private final PrescriptionOrderRepository prescriptionOrderRepository;
    private final EncounterFacade encounterFacade;
    private final InventoryFacade inventoryFacade;
    private final BillingFacade billingFacade;

    public ReviewSubstitutionService(
            SubstitutionProposalRepository substitutionProposalRepository,
            PrescriptionLineRepository prescriptionLineRepository,
            PrescriptionOrderRepository prescriptionOrderRepository,
            EncounterFacade encounterFacade,
            InventoryFacade inventoryFacade,
            BillingFacade billingFacade
    ) {
        this.substitutionProposalRepository = substitutionProposalRepository;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.prescriptionOrderRepository = prescriptionOrderRepository;
        this.encounterFacade = encounterFacade;
        this.inventoryFacade = inventoryFacade;
        this.billingFacade = billingFacade;
    }

    @Transactional
    public SubstitutionReviewResult review(ReviewSubstitutionCommand command) {
        if (command.decision() != SubstitutionStatus.APPROVED && command.decision() != SubstitutionStatus.REJECTED) {
            throw new IllegalArgumentException("A review decision must be APPROVED or REJECTED, not " + command.decision() + ".");
        }
        SubstitutionProposal proposal = substitutionProposalRepository.findById(command.proposalId())
                .orElseThrow(() -> new NoSuchElementException("Substitution proposal not found: " + command.proposalId()));
        if (proposal.status() != SubstitutionStatus.PROPOSED) {
            throw new SubstitutionAlreadyReviewedException(proposal.id());
        }
        if (proposal.proposedBy().equals(command.reviewedBy())) {
            throw new SubstitutionSelfReviewNotAllowedException();
        }

        if (command.decision() == SubstitutionStatus.REJECTED) {
            SubstitutionProposal rejected = substitutionProposalRepository.update(new SubstitutionProposal(
                    proposal.id(), proposal.prescriptionLineId(), proposal.proposedItemId(), proposal.reason(),
                    proposal.proposedBy(), proposal.proposedAt(), SubstitutionStatus.REJECTED, command.reviewedBy(),
                    Instant.now(), command.reviewNote(), null));
            return new SubstitutionReviewResult(rejected, null, null);
        }

        PrescriptionLine originalLine = prescriptionLineRepository.findById(proposal.prescriptionLineId())
                .orElseThrow(() -> new NoSuchElementException("Prescription line not found: " + proposal.prescriptionLineId()));
        PrescriptionLine cancelledLine = prescriptionLineRepository.update(
                originalLine.cancelForSubstitution("Substituted: " + proposal.reason()));

        billingFacade.voidUnpaidServiceCharge(originalLine.invoiceLineId(),
                "Substitution approved for prescription line " + originalLine.id(), command.reviewedBy());

        PrescriptionOrder order = prescriptionOrderRepository.findById(originalLine.prescriptionOrderId())
                .orElseThrow(() -> new NoSuchElementException("Prescription order not found: " + originalLine.prescriptionOrderId()));
        EncounterContext encounterContext = encounterFacade.findContext(order.encounterId())
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + order.encounterId()));
        ItemSummary replacementItem = inventoryFacade.findItemSummary(proposal.proposedItemId())
                .orElseThrow(() -> new NoSuchElementException("Item not found: " + proposal.proposedItemId()));

        UUID replacementLineId = UUID.randomUUID();
        UUID newInvoiceLineId = billingFacade.postServiceCharge(
                order.encounterId(), encounterContext.patientId(), encounterContext.facilityId(),
                encounterContext.payerType(), replacementItem.code(), originalLine.quantityPrescribed(),
                SUBSTITUTION_APPROVED_EVENT, replacementLineId, command.reviewedBy());

        // substitutionAllowed defaults to false on the replacement — a
        // fresh, explicit prescriber decision is required to allow
        // substituting it again, rather than an unbounded chain inheriting
        // the original permission.
        PrescriptionLine replacementLine = prescriptionLineRepository.insert(PrescriptionLine.create(
                order.id(), proposal.proposedItemId(), originalLine.quantityPrescribed(),
                originalLine.dosageInstructions(), newInvoiceLineId, false,
                originalLine.route(), originalLine.doseAmount(), originalLine.doseUnit(),
                originalLine.frequency(), originalLine.durationDays()).withId(replacementLineId));

        SubstitutionProposal approved = substitutionProposalRepository.update(new SubstitutionProposal(
                proposal.id(), proposal.prescriptionLineId(), proposal.proposedItemId(), proposal.reason(),
                proposal.proposedBy(), proposal.proposedAt(), SubstitutionStatus.APPROVED, command.reviewedBy(),
                Instant.now(), command.reviewNote(), replacementLine.id()));

        return new SubstitutionReviewResult(approved, cancelledLine, replacementLine);
    }
}
