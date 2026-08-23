package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemRepository;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.shared.kernel.EncounterContext;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

// 05-receptionist.md §7.2, 02-billing-architecture.md §10/§11: the
// receptionist's hand-built, multi-line invoice — DRAFT only, freely
// editable, not a financial record until IssueInvoiceService's separate
// explicit step assigns the number and locks it. §4.3 ⚠12: every line's
// price is resolved server-side from the payer's PriceList — the request
// carries a service item code and a quantity, never an amount.
//
// ⚠ Simplification: unlike ChargePostingService's event-driven charges,
// this does not apply SchemeCoverageRule insurance splitting — every
// line's full gross lands on patient_portion. Reasonable for the ad-hoc
// invoices this path is actually for (a pharmacy walk-in, a records
// fee); a receptionist billing an insured patient for something a
// coverage rule should split needs that built as its own pass before this
// path can be trusted for that case.
@Service
public class CreateDraftInvoiceService {

    private final ServiceItemRepository serviceItemRepository;
    private final PriceResolutionService priceResolutionService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final EncounterFacade encounterFacade;

    public CreateDraftInvoiceService(
            ServiceItemRepository serviceItemRepository,
            PriceResolutionService priceResolutionService,
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            EncounterFacade encounterFacade
    ) {
        this.serviceItemRepository = serviceItemRepository;
        this.priceResolutionService = priceResolutionService;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.encounterFacade = encounterFacade;
    }

    @Transactional
    public Invoice create(CreateDraftInvoiceCommand command) {
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("Add at least one line item.");
        }

        PayerCategory payerCategory = resolvePayerCategory(command.encounterId());
        UUID insurancePolicyId = null; // Phase scope: see the class-level simplification note.

        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(
                command.encounterId(), command.patientId(), command.facilityId(), payerCategory,
                insurancePolicyId, command.actorUserId()));

        for (CreateDraftInvoiceCommand.Line line : command.lines()) {
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero for " + line.serviceItemCode() + ".");
            }
            ServiceItem item = serviceItemRepository.findByCode(line.serviceItemCode())
                    .filter(ServiceItem::isActive)
                    .orElseThrow(() -> new NoSuchElementException("No active service item with code '" + line.serviceItemCode() + "'."));
            Money unitPrice = priceResolutionService.resolvePrice(line.serviceItemCode(), payerCategory);
            Money gross = new Money(unitPrice.amountMinor() * line.quantity(), unitPrice.currencyCode());

            // source_event_id is NOT NULL — there is no domain event behind
            // a hand-typed line, so this points at the one real thing that
            // caused it: the invoice it belongs to.
            invoiceLineRepository.insert(InvoiceLine.unauthorized(
                    draft.id(), item.id(), line.quantity(), gross, new Money(0, gross.currencyCode()), gross,
                    "ReceptionistInvoiceLine", draft.id()));
        }

        return draft;
    }

    private PayerCategory resolvePayerCategory(UUID encounterId) {
        if (encounterId == null) {
            return PayerCategory.CASH;
        }
        EncounterContext context = encounterFacade.findContext(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        return PayerCategory.fromPayerType(context.payerType());
    }
}
