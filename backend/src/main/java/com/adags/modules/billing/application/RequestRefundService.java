package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PaymentAllocation;
import com.adags.modules.billing.domain.PaymentAllocationNotSettledException;
import com.adags.modules.billing.domain.PaymentAllocationRepository;
import com.adags.modules.billing.domain.Refund;
import com.adags.modules.billing.domain.RefundRepository;
import com.adags.modules.billing.domain.RefundStatus;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 7 item 1: "Money going back out. Request -> ACCOUNTANT approves.
// Never a receptionist action. Second signature above the threshold."
// Below the threshold, this request IS the one required signature and
// applies immediately. Above it, the request lands PENDING for
// ReviewRefundService to decide.
@Service
public class RequestRefundService {

    private final PaymentAllocationRepository paymentAllocationRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final RefundRepository refundRepository;
    private final SecondSignatureThresholdResolver thresholdResolver;

    public RequestRefundService(
            PaymentAllocationRepository paymentAllocationRepository,
            InvoiceLineRepository invoiceLineRepository,
            InvoiceRepository invoiceRepository,
            RefundRepository refundRepository,
            SecondSignatureThresholdResolver thresholdResolver
    ) {
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.invoiceRepository = invoiceRepository;
        this.refundRepository = refundRepository;
        this.thresholdResolver = thresholdResolver;
    }

    @Transactional
    public Refund request(RequestRefundCommand command) {
        PaymentAllocation allocation = paymentAllocationRepository.findById(command.paymentAllocationId())
                .orElseThrow(() -> new NoSuchElementException("Payment allocation not found: " + command.paymentAllocationId()));
        if (!paymentAllocationRepository.isFundedBySettledPayment(allocation.id())) {
            throw new PaymentAllocationNotSettledException(allocation.id());
        }
        long remaining = allocation.amount().amountMinor() - allocation.refundedMinor();
        if (command.amountMinor() > remaining) {
            throw new IllegalArgumentException("Refund amount " + command.amountMinor()
                    + " exceeds what remains refundable (" + remaining + ") on this allocation.");
        }

        boolean requiresSecondSignature = thresholdResolver.requiresSecondSignature(command.amountMinor());

        Refund saved = refundRepository.insert(Refund.request(allocation.id(),
                new Money(command.amountMinor(), allocation.amount().currencyCode()),
                command.reason(), command.requestedBy(), requiresSecondSignature));

        if (!requiresSecondSignature) {
            paymentAllocationRepository.recordRefund(allocation.id(), saved.amount());
            InvoiceLine line = invoiceLineRepository.findById(allocation.invoiceLineId()).orElseThrow();
            invoiceRepository.recordRefund(line.invoiceId(), saved.amount());
            saved = refundRepository.decide(saved.id(), RefundStatus.APPLIED, command.requestedBy(),
                    "Applied at request — below the second-signature threshold.");
        }
        return saved;
    }
}
