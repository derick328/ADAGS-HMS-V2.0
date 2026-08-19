package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CancellationRequestStatus;
import com.adags.modules.billing.domain.InvoiceCancellationRequest;
import com.adags.modules.billing.domain.InvoiceCancellationRequestRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 18-admin.md §6: "Receptionist raises request (reason required) ->
// Invoice -> CANCELLATION_PENDING, frozen, no payments accepted." The
// "no payments accepted" half is already enforced elsewhere
// (PostPaymentService rejects CANCELLATION_PENDING invoices) — this just
// performs the transition. §6.1: reconsideration of a rejected request
// is a new row referencing the first (supersedesRequestId), never a
// reopened decision.
@Service
public class RequestInvoiceCancellationService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceCancellationRequestRepository cancellationRequestRepository;

    public RequestInvoiceCancellationService(
            InvoiceRepository invoiceRepository,
            InvoiceCancellationRequestRepository cancellationRequestRepository
    ) {
        this.invoiceRepository = invoiceRepository;
        this.cancellationRequestRepository = cancellationRequestRepository;
    }

    @Transactional
    public InvoiceCancellationRequest request(RequestInvoiceCancellationCommand command) {
        invoiceRepository.findById(command.invoiceId())
                .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + command.invoiceId()));

        if (command.supersedesRequestId() != null) {
            InvoiceCancellationRequest priorRequest = cancellationRequestRepository.findById(command.supersedesRequestId())
                    .orElseThrow(() -> new NoSuchElementException("Prior cancellation request not found: " + command.supersedesRequestId()));
            if (priorRequest.status() != CancellationRequestStatus.REJECTED) {
                throw new IllegalArgumentException(
                        "Reconsideration must reference a REJECTED request, not " + priorRequest.status() + ".");
            }
        }

        InvoiceCancellationRequest saved = cancellationRequestRepository.insert(InvoiceCancellationRequest.raise(
                command.invoiceId(), command.reason(), command.requestedBy(), command.supersedesRequestId()));
        invoiceRepository.markCancellationPending(command.invoiceId());
        return saved;
    }
}
