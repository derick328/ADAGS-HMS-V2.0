package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CancellationRequestStatus;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceCancellationRequest;
import com.adags.modules.billing.domain.InvoiceCancellationRequestAlreadyDecidedException;
import com.adags.modules.billing.domain.InvoiceCancellationRequestRepository;
import com.adags.modules.billing.domain.InvoiceCancellationRequiresRefundException;
import com.adags.modules.billing.domain.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 18-admin.md §6: approve -> VOID (charges reversed), or blocked into the
// refund flow if anything has been paid; reject -> back to ISSUED, "live
// and collectable... never left frozen after a decision," with the
// admin's note visible to the requester (review_note NOT NULL on
// REJECTED, enforced at the database — V45).
@Service
public class ReviewInvoiceCancellationService {

    private final InvoiceCancellationRequestRepository cancellationRequestRepository;
    private final InvoiceRepository invoiceRepository;

    public ReviewInvoiceCancellationService(
            InvoiceCancellationRequestRepository cancellationRequestRepository,
            InvoiceRepository invoiceRepository
    ) {
        this.cancellationRequestRepository = cancellationRequestRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public InvoiceCancellationRequest review(ReviewInvoiceCancellationCommand command) {
        if (command.decision() != CancellationRequestStatus.APPROVED && command.decision() != CancellationRequestStatus.REJECTED) {
            throw new IllegalArgumentException("A review decision must be APPROVED or REJECTED, not " + command.decision() + ".");
        }
        if (command.decision() == CancellationRequestStatus.REJECTED
                && (command.reviewNote() == null || command.reviewNote().isBlank())) {
            throw new IllegalArgumentException("A rejection note is required — it is shown to the requester.");
        }

        InvoiceCancellationRequest request = cancellationRequestRepository.findById(command.requestId())
                .orElseThrow(() -> new NoSuchElementException("Cancellation request not found: " + command.requestId()));
        if (request.status() != CancellationRequestStatus.PENDING) {
            throw new InvoiceCancellationRequestAlreadyDecidedException(request.id());
        }

        if (command.decision() == CancellationRequestStatus.REJECTED) {
            invoiceRepository.returnToIssuedFromCancellationPending(request.invoiceId());
        } else {
            Invoice invoice = invoiceRepository.findById(request.invoiceId())
                    .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + request.invoiceId()));
            // §10: "Once any payment has landed, void is blocked" — routed
            // to the refund flow (Phase 7 item 1) instead of silently
            // converting the decision. The admin refunds what was paid,
            // then approves this same request again.
            if (invoice.outstandingTotal().amountMinor() != invoice.grossTotal().amountMinor()) {
                throw new InvoiceCancellationRequiresRefundException(request.id(), invoice.id());
            }
            invoiceRepository.voidUnpaid(invoice.id(), request.reason(), command.reviewedBy());
        }

        return cancellationRequestRepository.decide(request.id(), command.decision(), command.reviewedBy(), command.reviewNote());
    }
}
