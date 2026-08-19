package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceNotWriteOffEligibleException;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.InvoiceWriteOff;
import com.adags.modules.billing.domain.InvoiceWriteOffRepository;
import com.adags.modules.billing.domain.WriteOffStatus;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 7 item 1: "Write-off with reason, ACCOUNTANT authority, second
// signature above a configurable threshold." Below the threshold, this
// request IS the one required signature and applies immediately. Above it,
// the request lands PENDING for ReviewInvoiceWriteOffService to decide.
@Service
public class RequestInvoiceWriteOffService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceWriteOffRepository invoiceWriteOffRepository;
    private final SecondSignatureThresholdResolver thresholdResolver;

    public RequestInvoiceWriteOffService(
            InvoiceRepository invoiceRepository,
            InvoiceWriteOffRepository invoiceWriteOffRepository,
            SecondSignatureThresholdResolver thresholdResolver
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceWriteOffRepository = invoiceWriteOffRepository;
        this.thresholdResolver = thresholdResolver;
    }

    @Transactional
    public InvoiceWriteOff request(RequestInvoiceWriteOffCommand command) {
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
                .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + command.invoiceId()));
        if (invoice.state() != InvoiceState.RECEIVABLE_POST_DISCHARGE) {
            throw new InvoiceNotWriteOffEligibleException(invoice.id(), invoice.state());
        }
        if (command.amountMinor() > invoice.outstandingTotal().amountMinor()) {
            throw new IllegalArgumentException("Write-off amount " + command.amountMinor()
                    + " exceeds outstanding balance " + invoice.outstandingTotal().amountMinor() + ".");
        }

        boolean requiresSecondSignature = thresholdResolver.requiresSecondSignature(command.amountMinor());

        InvoiceWriteOff saved = invoiceWriteOffRepository.insert(InvoiceWriteOff.request(invoice.id(),
                new Money(command.amountMinor(), invoice.outstandingTotal().currencyCode()),
                command.reason(), command.requestedBy(), requiresSecondSignature));

        if (!requiresSecondSignature) {
            invoiceRepository.recordWriteOff(invoice.id(), saved.amount());
            saved = invoiceWriteOffRepository.decide(saved.id(), WriteOffStatus.APPLIED, command.requestedBy(),
                    "Applied at request — below the second-signature threshold.");
        }
        return saved;
    }
}
