package com.adags.shared.web;

import com.adags.modules.billing.domain.BillingGateRejectedException;
import com.adags.modules.billing.domain.CreditNoteAlreadyDecidedException;
import com.adags.modules.billing.domain.CreditNoteExceedsOutstandingException;
import com.adags.modules.billing.domain.CreditNoteNotEligibleException;
import com.adags.modules.billing.domain.CreditNoteSelfApprovalNotAllowedException;
import com.adags.modules.billing.domain.InvoiceNotWriteOffEligibleException;
import com.adags.modules.billing.domain.InvoiceWriteOffAlreadyDecidedException;
import com.adags.modules.billing.domain.InvoiceWriteOffExceedsOutstandingException;
import com.adags.modules.billing.domain.InvoiceWriteOffSelfApprovalNotAllowedException;
import com.adags.modules.billing.domain.PaymentAllocationNotSettledException;
import com.adags.modules.billing.domain.RefundAlreadyDecidedException;
import com.adags.modules.billing.domain.RefundExceedsAllocationException;
import com.adags.modules.billing.domain.RefundSelfApprovalNotAllowedException;
import com.adags.modules.encounters.application.InvalidEncounterTransitionException;
import com.adags.modules.encounters.application.MissingCloseReasonException;
import com.adags.modules.inventory.domain.BatchExpiredException;
import com.adags.modules.inventory.domain.ControlledItemBlockedException;
import com.adags.modules.inventory.domain.DuplicateItemCodeException;
import com.adags.modules.inventory.domain.InsufficientStockException;
import com.adags.modules.inventory.domain.LabStockRequestExceedsRequestedException;
import com.adags.modules.inventory.domain.LabStockRequestNotOpenException;
import com.adags.modules.inventory.domain.PharmacyItemProposalAlreadyReviewedException;
import com.adags.modules.inventory.domain.PharmacyItemProposalSelfReviewNotAllowedException;
import com.adags.modules.laboratory.domain.CriticalNotificationAlreadyAcknowledgedException;
import com.adags.modules.laboratory.domain.ImplausibleResultValueException;
import com.adags.modules.laboratory.domain.MissingAmendmentReasonException;
import com.adags.modules.laboratory.domain.MissingMachineOutputException;
import com.adags.modules.laboratory.domain.MissingOverrideReasonException;
import com.adags.modules.laboratory.domain.MissingQualityNoteException;
import com.adags.modules.laboratory.domain.ResultNotPendingVerificationException;
import com.adags.modules.laboratory.domain.SpecimenNotProcessableException;
import com.adags.modules.patients.application.DuplicatePatientException;
import com.adags.modules.pharmacy.domain.DispenseExceedsPrescribedException;
import com.adags.modules.pharmacy.domain.InsufficientBatchStockException;
import com.adags.modules.pharmacy.domain.ItemNotDispensableException;
import com.adags.modules.pharmacy.domain.MissingFefoOverrideReasonException;
import com.adags.modules.pharmacy.domain.PrescriptionLineNotOpenException;
import com.adags.modules.pharmacy.domain.SubstitutionAlreadyReviewedException;
import com.adags.modules.pharmacy.domain.SubstitutionNotAllowedException;
import com.adags.modules.pharmacy.domain.SubstitutionSelfReviewNotAllowedException;
import com.adags.modules.referencedata.application.SynonymAlreadyReviewedException;
import com.adags.modules.referencedata.domain.SelfReviewNotAllowedException;
import com.adags.shared.security.InvalidCredentialsException;
import com.adags.shared.security.InvalidTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Session expired or invalid.");
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedBody(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DuplicatePatientException.class)
    public ProblemDetail handleDuplicatePatient(DuplicatePatientException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MissingCloseReasonException.class)
    public ProblemDetail handleMissingCloseReason(MissingCloseReasonException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidEncounterTransitionException.class)
    public ProblemDetail handleInvalidEncounterTransition(InvalidEncounterTransitionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SynonymAlreadyReviewedException.class)
    public ProblemDetail handleSynonymAlreadyReviewed(SynonymAlreadyReviewedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SelfReviewNotAllowedException.class)
    public ProblemDetail handleSelfReviewNotAllowed(SelfReviewNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MissingQualityNoteException.class)
    public ProblemDetail handleMissingQualityNote(MissingQualityNoteException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ImplausibleResultValueException.class)
    public ProblemDetail handleImplausibleResultValue(ImplausibleResultValueException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MissingOverrideReasonException.class)
    public ProblemDetail handleMissingOverrideReason(MissingOverrideReasonException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MissingMachineOutputException.class)
    public ProblemDetail handleMissingMachineOutput(MissingMachineOutputException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MissingAmendmentReasonException.class)
    public ProblemDetail handleMissingAmendmentReason(MissingAmendmentReasonException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(SpecimenNotProcessableException.class)
    public ProblemDetail handleSpecimenNotProcessable(SpecimenNotProcessableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ResultNotPendingVerificationException.class)
    public ProblemDetail handleResultNotPendingVerification(ResultNotPendingVerificationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CriticalNotificationAlreadyAcknowledgedException.class)
    public ProblemDetail handleCriticalNotificationAlreadyAcknowledged(CriticalNotificationAlreadyAcknowledgedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DuplicateItemCodeException.class)
    public ProblemDetail handleDuplicateItemCode(DuplicateItemCodeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 14-controlled-medicines-tanzania.md §2/§3: CONTROLLED is blocked from
    // catalogue activation, restock, prescribing and dispensing. 409, not
    // 400 — the request is well-formed, the item's regulatory status is
    // what forbids it (same treatment as SpecimenNotProcessableException).
    @ExceptionHandler(ControlledItemBlockedException.class)
    public ProblemDetail handleControlledItemBlocked(ControlledItemBlockedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("itemId", ex.itemId());
        return problem;
    }

    // root CLAUDE.md "Concurrency": two pharmacists dispensing the same
    // batch must not both succeed past available quantity — the loser
    // gets this, not a 500.
    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("batchId", ex.batchId());
        return problem;
    }

    @ExceptionHandler(LabStockRequestNotOpenException.class)
    public ProblemDetail handleLabStockRequestNotOpen(LabStockRequestNotOpenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("requestId", ex.requestId());
        return problem;
    }

    @ExceptionHandler(LabStockRequestExceedsRequestedException.class)
    public ProblemDetail handleLabStockRequestExceedsRequested(LabStockRequestExceedsRequestedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PharmacyItemProposalAlreadyReviewedException.class)
    public ProblemDetail handlePharmacyItemProposalAlreadyReviewed(PharmacyItemProposalAlreadyReviewedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PharmacyItemProposalSelfReviewNotAllowedException.class)
    public ProblemDetail handlePharmacyItemProposalSelfReviewNotAllowed(PharmacyItemProposalSelfReviewNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 12-pharmacist.md §6.3: hard-blocked, not flagged.
    @ExceptionHandler(BatchExpiredException.class)
    public ProblemDetail handleBatchExpired(BatchExpiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("batchId", ex.batchId());
        return problem;
    }

    // 12-pharmacist.md §4.4: "dispensed_total > prescribed -> rejected, hard block."
    @ExceptionHandler(DispenseExceedsPrescribedException.class)
    public ProblemDetail handleDispenseExceedsPrescribed(DispenseExceedsPrescribedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("prescriptionLineId", ex.prescriptionLineId());
        return problem;
    }

    // 14-controlled-medicines-tanzania.md §3: prescribing/dispensing a
    // controlled (or otherwise inactive) item, rejected server-side even
    // when a valid item_id is submitted directly.
    @ExceptionHandler(ItemNotDispensableException.class)
    public ProblemDetail handleItemNotDispensable(ItemNotDispensableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("itemId", ex.itemId());
        return problem;
    }

    @ExceptionHandler(MissingFefoOverrideReasonException.class)
    public ProblemDetail handleMissingFefoOverrideReason(MissingFefoOverrideReasonException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InsufficientBatchStockException.class)
    public ProblemDetail handleInsufficientBatchStock(InsufficientBatchStockException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("itemId", ex.itemId());
        return problem;
    }

    @ExceptionHandler(PrescriptionLineNotOpenException.class)
    public ProblemDetail handlePrescriptionLineNotOpen(PrescriptionLineNotOpenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("prescriptionLineId", ex.prescriptionLineId());
        return problem;
    }

    // 12-pharmacist.md §5.1: "The prescriber's substitution_allowed flag
    // governs whether a proposal is possible at all."
    @ExceptionHandler(SubstitutionNotAllowedException.class)
    public ProblemDetail handleSubstitutionNotAllowed(SubstitutionNotAllowedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("prescriptionLineId", ex.prescriptionLineId());
        return problem;
    }

    @ExceptionHandler(SubstitutionAlreadyReviewedException.class)
    public ProblemDetail handleSubstitutionAlreadyReviewed(SubstitutionAlreadyReviewedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SubstitutionSelfReviewNotAllowedException.class)
    public ProblemDetail handleSubstitutionSelfReviewNotAllowed(SubstitutionSelfReviewNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // backend/CLAUDE.md "API": "402 Payment Required when the billing gate
    // refuses, with the outstanding amount and the specific service in the
    // body so the UI can offer 'send to cashier'" — 02-billing-architecture.md
    // §3.3. Wired now even though no consuming endpoint exists yet, so the
    // first one (lab specimen collection, dispensing, procedure start)
    // inherits the correct response shape automatically.
    // Phase 7 item 1: write-off is only meaningful against a post-discharge
    // receivable — an ISSUED invoice still has the ordinary payment path.
    @ExceptionHandler(InvoiceNotWriteOffEligibleException.class)
    public ProblemDetail handleInvoiceNotWriteOffEligible(InvoiceNotWriteOffEligibleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // root CLAUDE.md "Concurrency": the WHERE clause on
    // InvoiceRepository.recordWriteOff is the real guard — this is what a
    // losing concurrent write-off (or one that outran the balance) gets.
    @ExceptionHandler(InvoiceWriteOffExceedsOutstandingException.class)
    public ProblemDetail handleInvoiceWriteOffExceedsOutstanding(InvoiceWriteOffExceedsOutstandingException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvoiceWriteOffAlreadyDecidedException.class)
    public ProblemDetail handleInvoiceWriteOffAlreadyDecided(InvoiceWriteOffAlreadyDecidedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // root CLAUDE.md "Authorisation": the second-person constraint above
    // the write-off threshold — the requester cannot also be the reviewer.
    @ExceptionHandler(InvoiceWriteOffSelfApprovalNotAllowedException.class)
    public ProblemDetail handleInvoiceWriteOffSelfApprovalNotAllowed(InvoiceWriteOffSelfApprovalNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Phase 7 item 1 (credit notes): eligible against any live invoice —
    // not DRAFT, VOID, or CANCELLATION_PENDING.
    @ExceptionHandler(CreditNoteNotEligibleException.class)
    public ProblemDetail handleCreditNoteNotEligible(CreditNoteNotEligibleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CreditNoteExceedsOutstandingException.class)
    public ProblemDetail handleCreditNoteExceedsOutstanding(CreditNoteExceedsOutstandingException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CreditNoteAlreadyDecidedException.class)
    public ProblemDetail handleCreditNoteAlreadyDecided(CreditNoteAlreadyDecidedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CreditNoteSelfApprovalNotAllowedException.class)
    public ProblemDetail handleCreditNoteSelfApprovalNotAllowed(CreditNoteSelfApprovalNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Phase 7 item 1 (refunds): "can only exist against a settled payment
    // allocation."
    @ExceptionHandler(PaymentAllocationNotSettledException.class)
    public ProblemDetail handlePaymentAllocationNotSettled(PaymentAllocationNotSettledException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RefundExceedsAllocationException.class)
    public ProblemDetail handleRefundExceedsAllocation(RefundExceedsAllocationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RefundAlreadyDecidedException.class)
    public ProblemDetail handleRefundAlreadyDecided(RefundAlreadyDecidedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RefundSelfApprovalNotAllowedException.class)
    public ProblemDetail handleRefundSelfApprovalNotAllowed(RefundSelfApprovalNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(BillingGateRejectedException.class)
    public ProblemDetail handleBillingGateRejected(BillingGateRejectedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        problem.setProperty("invoiceLineId", ex.invoiceLineId());
        problem.setProperty("serviceName", ex.serviceName());
        problem.setProperty("outstandingAmountMinor", ex.outstandingAmount().amountMinor());
        problem.setProperty("currencyCode", ex.outstandingAmount().currencyCode());
        return problem;
    }
}
