package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceNotWriteOffEligibleException;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.InvoiceWriteOff;
import com.adags.modules.billing.domain.InvoiceWriteOffRepository;
import com.adags.modules.billing.domain.WriteOffStatus;
import com.adags.shared.kernel.Money;
import com.adags.shared.settings.ProtocolSettingReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Phase 7 item 1: "Write-off with reason, ACCOUNTANT authority, second
// signature above a configurable threshold." Below the threshold, this
// request IS the one required signature and applies immediately. Above it,
// the request lands PENDING for ReviewInvoiceWriteOffService to decide.
@Service
public class RequestInvoiceWriteOffService {

    // root CLAUDE.md "Money": stored as an admin-tunable protocol_setting
    // (V43), not a constant — same reasoning as the patient duplicate-
    // detection thresholds. Default mirrors the seed row; only used if
    // that row is ever missing.
    static final String THRESHOLD_SETTING_KEY = "billing.write_off.second_signature_threshold_minor";
    private static final double DEFAULT_THRESHOLD_MINOR = 10_000_000;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceWriteOffRepository invoiceWriteOffRepository;
    private final ProtocolSettingReader protocolSettingReader;

    public RequestInvoiceWriteOffService(
            InvoiceRepository invoiceRepository,
            InvoiceWriteOffRepository invoiceWriteOffRepository,
            ProtocolSettingReader protocolSettingReader
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceWriteOffRepository = invoiceWriteOffRepository;
        this.protocolSettingReader = protocolSettingReader;
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

        double thresholdMinor = protocolSettingReader.getDouble(THRESHOLD_SETTING_KEY, DEFAULT_THRESHOLD_MINOR);
        boolean requiresSecondSignature = command.amountMinor() > thresholdMinor;

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
