package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PaymentAllocation;
import com.adags.modules.billing.domain.PaymentAllocationRepository;
import com.adags.modules.billing.domain.Refund;
import com.adags.modules.billing.domain.RefundAlreadyDecidedException;
import com.adags.modules.billing.domain.RefundRepository;
import com.adags.modules.billing.domain.RefundSelfApprovalNotAllowedException;
import com.adags.modules.billing.domain.RefundStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// The second signature for a refund that landed PENDING because it
// exceeded the threshold — a below-threshold refund is never PENDING
// (RequestRefundService applies it immediately).
@Service
public class ReviewRefundService {

    private final RefundRepository refundRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final InvoiceRepository invoiceRepository;

    public ReviewRefundService(
            RefundRepository refundRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            InvoiceLineRepository invoiceLineRepository,
            InvoiceRepository invoiceRepository
    ) {
        this.refundRepository = refundRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public Refund review(ReviewRefundCommand command) {
        if (command.decision() != RefundStatus.APPLIED && command.decision() != RefundStatus.REJECTED) {
            throw new IllegalArgumentException("A review decision must be APPLIED or REJECTED, not " + command.decision() + ".");
        }
        Refund refund = refundRepository.findById(command.refundId())
                .orElseThrow(() -> new NoSuchElementException("Refund not found: " + command.refundId()));
        if (refund.status() != RefundStatus.PENDING) {
            throw new RefundAlreadyDecidedException(refund.id());
        }
        if (refund.requestedBy().equals(command.decidedBy())) {
            throw new RefundSelfApprovalNotAllowedException();
        }

        if (command.decision() == RefundStatus.APPLIED) {
            PaymentAllocation allocation = paymentAllocationRepository.findById(refund.paymentAllocationId()).orElseThrow();
            paymentAllocationRepository.recordRefund(allocation.id(), refund.amount());
            InvoiceLine line = invoiceLineRepository.findById(allocation.invoiceLineId()).orElseThrow();
            invoiceRepository.recordRefund(line.invoiceId(), refund.amount());
        }
        return refundRepository.decide(refund.id(), command.decision(), command.decidedBy(), command.decisionNote());
    }
}
