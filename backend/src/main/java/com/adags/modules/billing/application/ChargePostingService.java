package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PriceNotConfiguredException;
import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemRepository;
import com.adags.modules.insurance.InsuranceFacade;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.CoverageSplit;
import com.adags.shared.kernel.Money;
import com.adags.shared.numbering.GaplessNumberAllocator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private final PatientFacade patientFacade;
    private final InsuranceFacade insuranceFacade;
    private final Clock clock;

    public ChargePostingService(
            ServiceItemRepository serviceItemRepository,
            PriceResolutionService priceResolutionService,
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            GaplessNumberAllocator numberAllocator,
            DomainEventPublisher eventPublisher,
            PatientFacade patientFacade,
            InsuranceFacade insuranceFacade,
            Clock clock
    ) {
        this.serviceItemRepository = serviceItemRepository;
        this.priceResolutionService = priceResolutionService;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.numberAllocator = numberAllocator;
        this.eventPublisher = eventPublisher;
        this.patientFacade = patientFacade;
        this.insuranceFacade = insuranceFacade;
        this.clock = clock;
    }

    @Transactional
    public ChargePostingResult postCharge(ChargePostingCommand command) {
        ServiceItem serviceItem = requireActiveServiceItem(command.serviceItemCode());
        Money unitPrice = priceResolutionService.resolvePrice(command.serviceItemCode(), command.payerCategory());
        Money gross = unitPrice.times(command.quantity());

        CoverageSplitResult split = resolveCoverageSplit(command.payerCategory(), command.patientId(), serviceItem, gross);

        return issueSingleLineInvoice(serviceItem, command.encounterId(), command.patientId(), command.facilityId(),
                command.payerCategory(), command.insurancePolicyId(), command.quantity(), gross,
                split.insurancePortion(), split.patientPortion(), split.coverageRuleId(),
                command.serviceItemCode(), command.sourceEventType(), command.sourceEventId(), command.actorUserId());
    }

    /**
     * 02-billing-architecture.md §6.2/§6.3: for an NHIF/PRIVATE_SCHEME
     * charge against a patient who actually holds an active policy on the
     * service date, resolve that policy's scheme's coverage rule and
     * split gross accordingly. Every other case — CASH/CORPORATE/STAFF,
     * or an insurance-category charge with no active policy on file —
     * falls through unchanged: the whole charge is the patient's portion,
     * byte-identical to every phase before this one.
     *
     * ⚠ Once a scheme is actually resolved, a missing coverage rule for
     * this item/category/date is NOT treated the same as "no policy" —
     * it throws (CoverageRuleNotConfiguredException, via InsuranceFacade).
     * A configuration gap is not a business answer; guessing EXCLUDED or
     * FULL mis-prices the patient or the scheme.
     */
    private CoverageSplitResult resolveCoverageSplit(PayerCategory payerCategory, UUID patientId,
                                                       ServiceItem serviceItem, Money gross) {
        if (payerCategory != PayerCategory.NHIF && payerCategory != PayerCategory.PRIVATE_SCHEME) {
            return CoverageSplitResult.patientPaysAll(gross);
        }
        LocalDate serviceDate = LocalDate.now(clock);
        Optional<UUID> schemeId = patientFacade.resolveActiveInsuranceSchemeId(patientId, serviceDate);
        if (schemeId.isEmpty()) {
            return CoverageSplitResult.patientPaysAll(gross);
        }
        CoverageSplit split = insuranceFacade.resolveCoverageSplit(
                schemeId.get(), serviceItem.id(), serviceItem.category().name(), gross, serviceDate);
        return new CoverageSplitResult(split.insurancePortion(), split.patientPortion(), split.coverageRuleId());
    }

    private record CoverageSplitResult(Money insurancePortion, Money patientPortion, UUID coverageRuleId) {
        static CoverageSplitResult patientPaysAll(Money gross) {
            return new CoverageSplitResult(new Money(0, gross.currencyCode()), gross, null);
        }
    }

    /**
     * 04-procedure-bedday-rules.md §3.5: the caller already resolved its
     * own price (Ward.daily_rate_minor) — same posting mechanics as
     * postCharge, but amount is used as gross directly instead of being
     * resolved via PriceResolutionService/price_list_entry. quantity is
     * always 1: a bed-day accrual row is one night, not a multiple.
     *
     * Known gap, flagged for Phase 7 (insurance): every other price in
     * this system resolves per payer category and is versioned — "close
     * valid_to, open a new row," never edited in place. A bed-day rate
     * currently does neither: it is one column on Ward, the same for
     * every payer, and changing it silently reprices every future night
     * with no history of what it used to be. NHIF likely reimburses a
     * different bed rate than cash. Leaving this as is until the
     * insurance module needs per-payer ward pricing anyway — see root
     * CLAUDE.md "Billing" for the versioning rule this doesn't yet follow.
     */
    @Transactional
    public ChargePostingResult postFixedAmountCharge(FixedAmountChargePostingCommand command) {
        ServiceItem serviceItem = requireActiveServiceItem(command.serviceItemCode());

        // Deliberately not run through resolveCoverageSplit — this caller
        // supplies its own pre-resolved amount rather than going through
        // PriceResolutionService (see this method's class-level comment:
        // "Known gap, flagged for Phase 7 (insurance)... leaving this as
        // is until the insurance module needs per-payer ward pricing
        // anyway"). Still out of scope here.
        return issueSingleLineInvoice(serviceItem, command.encounterId(), command.patientId(), command.facilityId(),
                command.payerCategory(), null, 1, command.amount(),
                new Money(0, command.amount().currencyCode()), command.amount(), null,
                command.serviceItemCode(), command.sourceEventType(), command.sourceEventId(), command.actorUserId());
    }

    private ServiceItem requireActiveServiceItem(String serviceItemCode) {
        return serviceItemRepository.findByCode(serviceItemCode)
                .filter(ServiceItem::isActive)
                .orElseThrow(() -> new PriceNotConfiguredException(
                        "No active service item with code '" + serviceItemCode + "'."));
    }

    private ChargePostingResult issueSingleLineInvoice(
            ServiceItem serviceItem, UUID encounterId, UUID patientId, UUID facilityId,
            PayerCategory payerCategory, UUID insurancePolicyId, int quantity, Money gross,
            Money insurancePortion, Money patientPortion, UUID coverageRuleId,
            String serviceItemCode, String sourceEventType, UUID sourceEventId, UUID actorUserId
    ) {
        Invoice draft = invoiceRepository.insertDraft(Invoice.draft(encounterId, patientId, facilityId,
                payerCategory, insurancePolicyId, actorUserId));

        InvoiceLine line = invoiceLineRepository.insert(InvoiceLine.unauthorized(draft.id(), serviceItem.id(),
                quantity, gross, insurancePortion, patientPortion, coverageRuleId, sourceEventType, sourceEventId));

        String invoiceNumber = numberAllocator.allocate(
                INVOICE_NUMBER_SEQUENCE, facilityId, LocalDate.now(clock).getYear(), INVOICE_NUMBER_PAD_WIDTH);
        Invoice issued = invoiceRepository.issue(draft.id(), invoiceNumber, draft.version());

        eventPublisher.publish("ChargePosted", Map.of(
                "invoiceId", issued.id().toString(),
                "invoiceLineId", line.id().toString(),
                "encounterId", encounterId.toString(),
                "serviceItemCode", serviceItemCode,
                "grossMinor", gross.amountMinor(),
                "sourceEventType", sourceEventType,
                "sourceEventId", sourceEventId.toString()));
        eventPublisher.publish("InvoiceIssued", Map.of(
                "invoiceId", issued.id().toString(),
                "invoiceNumber", issued.invoiceNumber(),
                "encounterId", encounterId.toString()));

        return new ChargePostingResult(issued.id(), issued.invoiceNumber(), line.id(), gross, patientPortion);
    }
}
