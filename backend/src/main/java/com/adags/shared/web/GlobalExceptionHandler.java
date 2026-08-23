package com.adags.shared.web;

import com.adags.modules.billing.domain.BillingGateRejectedException;
import com.adags.modules.billing.domain.CreditNoteAlreadyDecidedException;
import com.adags.modules.billing.domain.CreditNoteExceedsOutstandingException;
import com.adags.modules.billing.domain.CreditNoteNotEligibleException;
import com.adags.modules.billing.domain.CreditNoteSelfApprovalNotAllowedException;
import com.adags.modules.billing.domain.InvoiceAlreadyHasPendingCancellationException;
import com.adags.modules.billing.domain.InvoiceCancellationNotEligibleException;
import com.adags.modules.billing.domain.InvoiceCancellationRequestAlreadyDecidedException;
import com.adags.modules.billing.domain.InvoiceCancellationRequiresRefundException;
import com.adags.modules.billing.domain.InvoiceNotWriteOffEligibleException;
import com.adags.modules.billing.domain.InvoiceWriteOffAlreadyDecidedException;
import com.adags.modules.billing.domain.InvoiceWriteOffExceedsOutstandingException;
import com.adags.modules.billing.domain.InvoiceWriteOffSelfApprovalNotAllowedException;
import com.adags.modules.billing.domain.PaymentAllocationNotSettledException;
import com.adags.modules.billing.domain.RefundAlreadyDecidedException;
import com.adags.modules.billing.domain.RefundExceedsAllocationException;
import com.adags.modules.billing.domain.RefundSelfApprovalNotAllowedException;
import com.adags.modules.clinical.domain.ImplausibleVitalException;
import com.adags.modules.clinical.domain.VitalReferenceRangeNotConfiguredException;
import com.adags.modules.encounters.application.InvalidEncounterTransitionException;
import com.adags.modules.encounters.application.MissingCloseReasonException;
import com.adags.modules.encounters.application.MissingReferralException;
import com.adags.modules.encounters.domain.InvalidTriageAssessmentException;
import com.adags.modules.encounters.domain.StaleEncounterException;
import com.adags.modules.finance.domain.BatchSelfDisbursementNotAllowedException;
import com.adags.modules.finance.domain.DuplicateBatchException;
import com.adags.modules.finance.domain.DuplicateSalaryRecordException;
import com.adags.modules.finance.domain.ExpenseSelfApprovalNotAllowedException;
import com.adags.modules.finance.domain.InvalidBatchTransitionException;
import com.adags.modules.finance.domain.InvalidExpenseTransitionException;
import com.adags.modules.identity.domain.DuplicateUsernameException;
import com.adags.modules.identity.domain.IncorrectCurrentPasswordException;
import com.adags.modules.identity.domain.RoleAssignmentAlreadyEndedException;
import com.adags.modules.identity.domain.SelfRoleGrantNotAllowedException;
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
import com.adags.modules.surgery.domain.AnaesthesiaNotRecordedException;
import com.adags.modules.surgery.domain.ChecklistNotSatisfiedException;
import com.adags.modules.surgery.domain.ConsentNotObtainedException;
import com.adags.modules.surgery.domain.DispositionAlreadyRecordedException;
import com.adags.modules.surgery.domain.EmergencyReductionNotAllowedException;
import com.adags.modules.surgery.domain.InvalidSurgeryOrderTransitionException;
import com.adags.modules.surgery.domain.MissingCountDiscrepancyNoteException;
import com.adags.modules.surgery.domain.SurgeryOrderNotCompletedException;
import com.adags.modules.surgery.domain.MissingWastageReasonException;
import com.adags.modules.surgery.domain.ProcedureItemAlreadyAttestedException;
import com.adags.modules.surgery.domain.ProcedureItemAlreadyReconciledException;
import com.adags.modules.surgery.domain.ProcedureItemNotBalancedException;
import com.adags.modules.surgery.domain.ProcedureItemNotReconciledException;
import com.adags.modules.surgery.domain.ProcedureItemSelfReconciliationNotAllowedException;
import com.adags.modules.surgery.domain.SurgeryOrderNotOpenException;
import com.adags.modules.surgery.domain.SurgeryReconciliationIncompleteException;
import com.adags.modules.surgery.domain.TheatreSlotNotAvailableException;
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

    // 05-receptionist.md §4.1: "warn, show candidates" needs the candidates
    // themselves, not just a count buried in a message string — the
    // receptionist has to see who they might be duplicating.
    @ExceptionHandler(DuplicatePatientException.class)
    public ProblemDetail handleDuplicatePatient(DuplicatePatientException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("candidates", ex.candidates());
        return problem;
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

    // Phase 7 item 2: invoice cancellation lifecycle (18-admin.md §6).
    @ExceptionHandler(InvoiceAlreadyHasPendingCancellationException.class)
    public ProblemDetail handleInvoiceAlreadyHasPendingCancellation(InvoiceAlreadyHasPendingCancellationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvoiceCancellationNotEligibleException.class)
    public ProblemDetail handleInvoiceCancellationNotEligible(InvoiceCancellationNotEligibleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvoiceCancellationRequestAlreadyDecidedException.class)
    public ProblemDetail handleInvoiceCancellationRequestAlreadyDecided(InvoiceCancellationRequestAlreadyDecidedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // §10 / §6.2: "Once payment has landed... that is a refund, not a
    // void." 409, not 400 — the request is well-formed, the invoice's
    // payment state is what blocks it (same treatment as
    // ControlledItemBlockedException).
    @ExceptionHandler(InvoiceCancellationRequiresRefundException.class)
    public ProblemDetail handleInvoiceCancellationRequiresRefund(InvoiceCancellationRequiresRefundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("invoiceId", ex.invoiceId());
        return problem;
    }

    // Phase 7 item 4 (expenses): DRAFT -> PENDING_APPROVAL -> APPROVED |
    // VOID — the repository's WHERE-guarded UPDATE is the real guard.
    @ExceptionHandler(InvalidExpenseTransitionException.class)
    public ProblemDetail handleInvalidExpenseTransition(InvalidExpenseTransitionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // root CLAUDE.md "Authorisation": CHECK (approved_by != created_by) on
    // expenses — "one person recording, approving, and paying is the
    // standard embezzlement path."
    @ExceptionHandler(ExpenseSelfApprovalNotAllowedException.class)
    public ProblemDetail handleExpenseSelfApprovalNotAllowed(ExpenseSelfApprovalNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Phase 7 item 4 (payroll): PENDING -> DISBURSED -> REVERSED.
    @ExceptionHandler(InvalidBatchTransitionException.class)
    public ProblemDetail handleInvalidBatchTransition(InvalidBatchTransitionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // root CLAUDE.md "Authorisation": CHECK (generated_by != disbursed_by).
    @ExceptionHandler(BatchSelfDisbursementNotAllowedException.class)
    public ProblemDetail handleBatchSelfDisbursementNotAllowed(BatchSelfDisbursementNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // root CLAUDE.md "Concurrency": UNIQUE (batch_type, month, year) —
    // "running twice for one month must not pay twice."
    @ExceptionHandler(DuplicateBatchException.class)
    public ProblemDetail handleDuplicateBatch(DuplicateBatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DuplicateSalaryRecordException.class)
    public ProblemDetail handleDuplicateSalaryRecord(DuplicateSalaryRecordException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Phase 8 item 1: "Consent blocks SCHEDULING, not billing. No signed
    // document, no theatre slot." Well-formed request, missing precondition
    // — same treatment as MissingCloseReasonException.
    @ExceptionHandler(ConsentNotObtainedException.class)
    public ProblemDetail handleConsentNotObtained(ConsentNotObtainedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // root CLAUDE.md "Concurrency": idx_theatre_slot_not_cancelled is the
    // real guard against two procedures in one theatre slot — the loser of
    // the race gets this, not a 500. Same treatment as BedNotAvailableException.
    @ExceptionHandler(TheatreSlotNotAvailableException.class)
    public ProblemDetail handleTheatreSlotNotAvailable(TheatreSlotNotAvailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("theatreId", ex.theatreId());
        return problem;
    }

    // Phase 8 item 2: a well-formed prepare request against a surgery
    // order that has already reached COMPLETED/CANCELLED — same treatment
    // as PrescriptionLineNotOpenException.
    @ExceptionHandler(SurgeryOrderNotOpenException.class)
    public ProblemDetail handleSurgeryOrderNotOpen(SurgeryOrderNotOpenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("surgeryOrderId", ex.surgeryOrderId());
        return problem;
    }

    // Phase 8 item 3: repository's WHERE-guarded status UPDATE is the
    // real guard — same treatment as InvalidEncounterTransitionException.
    @ExceptionHandler(InvalidSurgeryOrderTransitionException.class)
    public ProblemDetail handleInvalidSurgeryOrderTransition(InvalidSurgeryOrderTransitionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MissingWastageReasonException.class)
    public ProblemDetail handleMissingWastageReason(MissingWastageReasonException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 04-procedure-bedday-rules.md §2.3: "prepared == used + returned +
    // wasted" — a reconciliation entry that doesn't add up.
    @ExceptionHandler(ProcedureItemNotBalancedException.class)
    public ProblemDetail handleProcedureItemNotBalanced(ProcedureItemNotBalancedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ProcedureItemAlreadyReconciledException.class)
    public ProblemDetail handleProcedureItemAlreadyReconciled(ProcedureItemAlreadyReconciledException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ProcedureItemNotReconciledException.class)
    public ProblemDetail handleProcedureItemNotReconciled(ProcedureItemNotReconciledException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ProcedureItemAlreadyAttestedException.class)
    public ProblemDetail handleProcedureItemAlreadyAttested(ProcedureItemAlreadyAttestedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // root CLAUDE.md "Authorisation": the circulating nurse and the
    // attesting surgeon must be different people.
    @ExceptionHandler(ProcedureItemSelfReconciliationNotAllowedException.class)
    public ProblemDetail handleProcedureItemSelfReconciliationNotAllowed(ProcedureItemSelfReconciliationNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Phase 8 item 4: WHO-checklist pauses. "Each pause BLOCKS the next
    // stage" — well-formed request, blocked by state, not input.
    @ExceptionHandler(ChecklistNotSatisfiedException.class)
    public ProblemDetail handleChecklistNotSatisfied(ChecklistNotSatisfiedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MissingCountDiscrepancyNoteException.class)
    public ProblemDetail handleMissingCountDiscrepancyNote(MissingCountDiscrepancyNoteException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(EmergencyReductionNotAllowedException.class)
    public ProblemDetail handleEmergencyReductionNotAllowed(EmergencyReductionNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Phase 8 item 7: "A procedure requiring anaesthesia cannot reach
    // COMPLETED without either this pointer or an explicit no_anaesthesia
    // flag."
    @ExceptionHandler(AnaesthesiaNotRecordedException.class)
    public ProblemDetail handleAnaesthesiaNotRecorded(AnaesthesiaNotRecordedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SurgeryReconciliationIncompleteException.class)
    public ProblemDetail handleSurgeryReconciliationIncomplete(SurgeryReconciliationIncompleteException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Phase 8 item 5: disposition only meaningful once COMPLETED.
    @ExceptionHandler(SurgeryOrderNotCompletedException.class)
    public ProblemDetail handleSurgeryOrderNotCompleted(SurgeryOrderNotCompletedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DispositionAlreadyRecordedException.class)
    public ProblemDetail handleDispositionAlreadyRecorded(DispositionAlreadyRecordedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 18-admin.md §4: username uniqueness is a UNIQUE constraint on
    // app_user — the request is well-formed, the value collides.
    @ExceptionHandler(DuplicateUsernameException.class)
    public ProblemDetail handleDuplicateUsername(DuplicateUsernameException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 18-admin.md §11: "Admin cannot silently elevate themselves." The
    // friendly half of the V57 DB CHECK backstop.
    @ExceptionHandler(SelfRoleGrantNotAllowedException.class)
    public ProblemDetail handleSelfRoleGrantNotAllowed(SelfRoleGrantNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RoleAssignmentAlreadyEndedException.class)
    public ProblemDetail handleRoleAssignmentAlreadyEnded(RoleAssignmentAlreadyEndedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Not InvalidCredentialsException/401 — this is a signed-in user changing
    // their own password, not a login attempt, and 401 on an authenticated
    // request risks the frontend treating it as session expiry.
    @ExceptionHandler(IncorrectCurrentPasswordException.class)
    public ProblemDetail handleIncorrectCurrentPassword(IncorrectCurrentPasswordException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 06-triage.md §5: "referral can never be blank" — rejected outright,
    // never defaulted to a fallback doctor.
    @ExceptionHandler(MissingReferralException.class)
    public ProblemDetail handleMissingReferral(MissingReferralException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // TriageAssessment.create()'s single catch-all for a malformed
    // submission — missing required field, a conditional field violating
    // its has_pain/fall_risk/IMMEDIATE gate, an out-of-range score.
    @ExceptionHandler(InvalidTriageAssessmentException.class)
    public ProblemDetail handleInvalidTriageAssessment(InvalidTriageAssessmentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 06-triage.md §2.2: "Implausible values are rejected with a message
    // naming the bound, never silently clamped." The exception message
    // already names the bound — nothing further to attach.
    @ExceptionHandler(ImplausibleVitalException.class)
    public ProblemDetail handleImplausibleVital(ImplausibleVitalException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // A configuration gap (no VitalReferenceRange for this age band), not
    // a bad submission — 409, same "well-formed request blocked by
    // non-input state" treatment as ControlledItemBlockedException.
    @ExceptionHandler(VitalReferenceRangeNotConfiguredException.class)
    public ProblemDetail handleVitalReferenceRangeNotConfigured(VitalReferenceRangeNotConfiguredException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // backend/CLAUDE.md "API": "409 Conflict on optimistic lock failure,
    // with the current version." The exception carries expectedVersion —
    // the version the submitting client read; the UI's job is "reload and
    // resubmit" against whatever the row's real current version now is.
    @ExceptionHandler(StaleEncounterException.class)
    public ProblemDetail handleStaleEncounter(StaleEncounterException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("encounterId", ex.encounterId());
        problem.setProperty("expectedVersion", ex.expectedVersion());
        return problem;
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
