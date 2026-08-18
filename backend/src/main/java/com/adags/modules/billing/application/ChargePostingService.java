package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PriceNotConfiguredException;
import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemRepository;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.Money;
import com.adags.shared.numbering.GaplessNumberAllocator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

// 02-billing-architecture.md §4: "Every charge is posted by a domain
// event. There is no manual charge-entry path." The one seam every
// event-driven charge (consultation today, later orders/prescriptions)
// goes through — resolves the price via PriceResolutionService (§4.2:
// nobody types a price, a missing price blocks rather than defaults to
// zero) and posts it as a freshly issued, single-line invoice.
//
// DRAFT and immediately issue in the same call: unlike a receptionist's
// hand-entered invoice, there is no typo to protect against here — the
// amount came from the price list, not a keyboard — so the DRAFT window
// that exists to make manual entry correctable before it becomes a
// numbered financial document buys nothing for a system-posted charge.
@Service
public class ChargePostingService {

    private static final String INVOICE_NUMBER_SEQUENCE = "INVOICE_NO";
    private static final int INVOICE_NUMBER_PAD_WIDTH = 6;

    private final ServiceItemRepository serviceItemRepository;
    private final PriceResolutionService priceResolutionService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final GaplessNumberAllocator numberAllocator;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ChargePostingService(
            ServiceItemRepository serviceItemRepository,
            PriceResolutionService priceResolutionService,
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            GaplessNumberAllocator numberAllocator,
            DomainEventPublisher eventPublisher,
            Clock clock
    ) {
        this.serviceItemRepository = serviceItemRepository;
        this.priceResolutionService = priceResolutionService;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.numberAllocator = numberAllocator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ChargePostingResult postCharge(ChargePostingCommand command) {
        ServiceItem serviceItem = serviceItemRepository.findByCode(command.serviceItemCode())
                .filter(ServiceItem::isActive)
                .orElseThrow(() -> new PriceNotConfiguredException(
                        "No active service item with code '" + command.serviceItemCode() + "'."));

        Money unitPrice = priceResolutionService.resolvePrice(command.serviceItemCode(), command.payerCategory());
        Money gross = unitPrice.times(command.quantity());
        // No insurance module yet: the whole charge is the patient's
        // portion until SchemeCoverageRule exists to split it (§6.3).
        Money insurancePortion = new Money(0, gross.currencyCode());
        Money patientPortion = gross;

        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(command.encounterId(), command.patientId(),
                command.facilityId(), command.payerCategory(), command.insurancePolicyId(), command.actorUserId()));

        InvoiceLine line = invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItem.id(),
                command.quantity(), gross, insurancePortion, patientPortion, command.sourceEventType(),
                command.sourceEventId()));

        String invoiceNumber = numberAllocator.allocate(
                INVOICE_NUMBER_SEQUENCE, command.facilityId(), LocalDate.now(clock).getYear(), INVOICE_NUMBER_PAD_WIDTH);
        Invoice issued = invoiceRepository.issue(draft.id(), invoiceNumber, draft.version());

        eventPublisher.publish("ChargePosted", Map.of(
                "invoiceId", issued.id().toString(),
                "invoiceLineId", line.id().toString(),
                "encounterId", command.encounterId().toString(),
                "serviceItemCode", command.serviceItemCode(),
                "grossMinor", gross.amountMinor(),
                "sourceEventType", command.sourceEventType(),
                "sourceEventId", command.sourceEventId().toString()));
        eventPublisher.publish("InvoiceIssued", Map.of(
                "invoiceId", issued.id().toString(),
                "invoiceNumber", issued.invoiceNumber(),
                "encounterId", command.encounterId().toString()));

        return new ChargePostingResult(issued.id(), issued.invoiceNumber(), line.id(), gross, patientPortion);
    }
}
