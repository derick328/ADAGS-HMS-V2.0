package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.shared.numbering.GaplessNumberAllocator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

// 02-billing-architecture.md §10: the two-step issue's second step —
// assigns the invoice number from the gapless per-facility per-year
// sequence and locks the lines. Never at draft (same numbering discipline
// as RegisterPatientService's hospital number).
@Service
public class IssueInvoiceService {

    private static final String INVOICE_NUMBER_SEQUENCE = "INVOICE_NO";
    private static final int INVOICE_NUMBER_PAD_WIDTH = 6;

    private final InvoiceRepository invoiceRepository;
    private final GaplessNumberAllocator numberAllocator;
    private final Clock clock;

    public IssueInvoiceService(InvoiceRepository invoiceRepository, GaplessNumberAllocator numberAllocator, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.numberAllocator = numberAllocator;
        this.clock = clock;
    }

    @Transactional
    public Invoice issue(UUID invoiceId, UUID facilityId, int expectedVersion) {
        String invoiceNumber = numberAllocator.allocate(
                INVOICE_NUMBER_SEQUENCE, facilityId, LocalDate.now(clock).getYear(), INVOICE_NUMBER_PAD_WIDTH);
        return invoiceRepository.issue(invoiceId, invoiceNumber, expectedVersion);
    }
}
