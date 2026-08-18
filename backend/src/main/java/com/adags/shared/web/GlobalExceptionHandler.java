package com.adags.shared.web;

import com.adags.modules.billing.domain.BillingGateRejectedException;
import com.adags.modules.encounters.application.InvalidEncounterTransitionException;
import com.adags.modules.encounters.application.MissingCloseReasonException;
import com.adags.modules.laboratory.domain.CriticalNotificationAlreadyAcknowledgedException;
import com.adags.modules.laboratory.domain.ImplausibleResultValueException;
import com.adags.modules.laboratory.domain.MissingAmendmentReasonException;
import com.adags.modules.laboratory.domain.MissingMachineOutputException;
import com.adags.modules.laboratory.domain.MissingOverrideReasonException;
import com.adags.modules.laboratory.domain.MissingQualityNoteException;
import com.adags.modules.laboratory.domain.ResultNotPendingVerificationException;
import com.adags.modules.laboratory.domain.SpecimenNotProcessableException;
import com.adags.modules.patients.application.DuplicatePatientException;
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

    // backend/CLAUDE.md "API": "402 Payment Required when the billing gate
    // refuses, with the outstanding amount and the specific service in the
    // body so the UI can offer 'send to cashier'" — 02-billing-architecture.md
    // §3.3. Wired now even though no consuming endpoint exists yet, so the
    // first one (lab specimen collection, dispensing, procedure start)
    // inherits the correct response shape automatically.
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
