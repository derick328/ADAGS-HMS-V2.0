package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CreditNote;
import com.adags.modules.billing.domain.CreditNoteNotEligibleException;
import com.adags.modules.billing.domain.CreditNoteRepository;
import com.adags.modules.billing.domain.CreditNoteStatus;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Set;

// Phase 7 item 1: "Reduces what a patient owes without money moving...
// ACCOUNTANT authority, reason required." Broader eligibility than
// write-off — any live invoice, not only a post-discharge receivable.
@Service
public class IssueCreditNoteService {

    private static final Set<InvoiceState> INELIGIBLE_STATES =
            EnumSet.of(InvoiceState.DRAFT, InvoiceState.VOID, InvoiceState.CANCELLATION_PENDING);

    private final InvoiceRepository invoiceRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final SecondSignatureThresholdResolver thresholdResolver;

    public IssueCreditNoteService(
            InvoiceRepository invoiceRepository,
            CreditNoteRepository creditNoteRepository,
            SecondSignatureThresholdResolver thresholdResolver
    ) {
        this.invoiceRepository = invoiceRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.thresholdResolver = thresholdResolver;
    }

    @Transactional
    public CreditNote issue(IssueCreditNoteCommand command) {
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
                .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + command.invoiceId()));
        if (INELIGIBLE_STATES.contains(invoice.state())) {
            throw new CreditNoteNotEligibleException(invoice.id(), invoice.state());
        }
        if (command.amountMinor() > invoice.outstandingTotal().amountMinor()) {
            throw new IllegalArgumentException("Credit note amount " + command.amountMinor()
                    + " exceeds outstanding balance " + invoice.outstandingTotal().amountMinor() + ".");
        }

        boolean requiresSecondSignature = thresholdResolver.requiresSecondSignature(command.amountMinor());

        CreditNote saved = creditNoteRepository.insert(CreditNote.issue(invoice.id(),
                new Money(command.amountMinor(), invoice.outstandingTotal().currencyCode()),
                command.reason(), command.reference(), command.issuedBy(), requiresSecondSignature));

        if (!requiresSecondSignature) {
            invoiceRepository.recordCreditNote(invoice.id(), saved.amount());
            saved = creditNoteRepository.decide(saved.id(), CreditNoteStatus.APPLIED, command.issuedBy(),
                    "Applied at issue — below the second-signature threshold.");
        }
        return saved;
    }
}
