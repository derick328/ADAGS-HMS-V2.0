package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.BillingFacade;
import com.adags.modules.billing.application.ChargePostingCommand;
import com.adags.modules.billing.application.ChargePostingResult;
import com.adags.modules.billing.application.ChargePostingService;
import com.adags.modules.billing.application.FixedAmountChargePostingCommand;
import com.adags.modules.billing.application.SetPharmacyItemPriceCommand;
import com.adags.modules.billing.application.SetPharmacyItemPriceService;
import com.adags.modules.billing.domain.BillingGate;
import com.adags.modules.billing.domain.PriceListEntry;
import com.adags.modules.billing.domain.InvoiceCannotBeVoidedException;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PayerCategory;
import com.adags.modules.billing.domain.PaymentBypass;
import com.adags.modules.billing.domain.PaymentBypassRepository;
import com.adags.modules.billing.domain.PaymentBypassSource;
import com.adags.modules.billing.domain.PriceNotConfiguredException;
import com.adags.modules.billing.domain.ProviderBillingCategory;
import com.adags.modules.billing.domain.ProviderBillingCategoryRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import com.adags.shared.authz.RoleAssignmentView;
import com.adags.shared.kernel.Money;
import com.adags.shared.kernel.PayerType;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class BillingFacadeImpl implements BillingFacade {

    private static final String TRIAGE_COMPLETED_EVENT = "TriageCompleted";

    private final PaymentBypassRepository paymentBypassRepository;
    private final ChargePostingService chargePostingService;
    private final InvoiceLineRepository invoiceLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingGate billingGate;
    private final AuthorizationService authorizationService;
    private final ProviderBillingCategoryRepository providerBillingCategoryRepository;
    private final SetPharmacyItemPriceService setPharmacyItemPriceService;

    public BillingFacadeImpl(
            PaymentBypassRepository paymentBypassRepository,
            ChargePostingService chargePostingService,
            InvoiceLineRepository invoiceLineRepository,
            InvoiceRepository invoiceRepository,
            BillingGate billingGate,
            AuthorizationService authorizationService,
            ProviderBillingCategoryRepository providerBillingCategoryRepository,
            SetPharmacyItemPriceService setPharmacyItemPriceService
    ) {
        this.paymentBypassRepository = paymentBypassRepository;
        this.chargePostingService = chargePostingService;
        this.invoiceLineRepository = invoiceLineRepository;
        this.invoiceRepository = invoiceRepository;
        this.billingGate = billingGate;
        this.authorizationService = authorizationService;
        this.providerBillingCategoryRepository = providerBillingCategoryRepository;
        this.setPharmacyItemPriceService = setPharmacyItemPriceService;
    }

    @Override
    public UUID createImmediateTriagePaymentBypass(UUID encounterId, String reason, UUID grantedBy) {
        PaymentBypass saved = paymentBypassRepository.insert(
                PaymentBypass.grant(encounterId, PaymentBypassSource.TRIAGE_IMMEDIATE, reason, grantedBy));
        return saved.id();
    }

    @Override
    public void postConsultationCharge(UUID encounterId, UUID patientId, UUID facilityId, PayerType payerType,
                                        UUID triageAssessmentId, UUID assignedProviderId, UUID actorUserId) {
        String serviceItemCode = resolveConsultationServiceItemCode(assignedProviderId);
        // No insurance module: NHIF is named explicitly in V15's seed
        // comment as the dominant scheme, a reasonable placeholder — it
        // resolves to the same PriceNotConfiguredException as any other
        // unseeded category today regardless, since only CASH has prices.
        PayerCategory payerCategory = payerType == PayerType.CASH ? PayerCategory.CASH : PayerCategory.NHIF;

        chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, facilityId, payerCategory, null,
                serviceItemCode, 1, TRIAGE_COMPLETED_EVENT, triageAssessmentId, actorUserId));
    }

    @Override
    public void authorizeConsultationEntry(UUID encounterId, UUID actorUserId) {
        InvoiceLine line = requireConsultationLine(encounterId);
        billingGate.authorizeConsumption(line.id(), actorUserId);
    }

    @Override
    public void voidUnpaidConsultationCharge(UUID encounterId, String reason, UUID actorUserId) {
        InvoiceLine line = requireConsultationLine(encounterId);
        invoiceRepository.voidUnpaid(line.invoiceId(), reason, actorUserId);
    }

    private InvoiceLine requireConsultationLine(UUID encounterId) {
        return invoiceLineRepository.findConsultationLineByEncounterId(encounterId)
                .orElseThrow(() -> new NoSuchElementException("No consultation charge for encounter " + encounterId));
    }

    @Override
    public boolean voidUnpaidServiceCharge(UUID invoiceLineId, String reason, UUID actorUserId) {
        InvoiceLine line = invoiceLineRepository.findById(invoiceLineId)
                .orElseThrow(() -> new NoSuchElementException("Invoice line not found: " + invoiceLineId));
        try {
            invoiceRepository.voidUnpaid(line.invoiceId(), reason, actorUserId);
            return true;
        } catch (InvoiceCannotBeVoidedException alreadySettled) {
            return false;
        }
    }

    @Override
    public UUID setPharmacyItemPrice(String itemCode, String itemName, long priceAmountMinor, UUID actorUserId) {
        PriceListEntry entry = setPharmacyItemPriceService.setPrice(
                new SetPharmacyItemPriceCommand(itemCode, itemName, priceAmountMinor, actorUserId));
        return entry.id();
    }

    @Override
    public Optional<Money> findCurrentPharmacyItemPrice(String itemCode) {
        return setPharmacyItemPriceService.findCurrentPrice(itemCode);
    }

    @Override
    public UUID postServiceCharge(UUID encounterId, UUID patientId, UUID facilityId, PayerType payerType,
                                   String serviceItemCode, int quantity, String sourceEventType, UUID sourceEventId,
                                   UUID actorUserId) {
        PayerCategory payerCategory = toPayerCategory(payerType);
        ChargePostingResult result = chargePostingService.postCharge(new ChargePostingCommand(
                encounterId, patientId, facilityId, payerCategory, null,
                serviceItemCode, quantity, sourceEventType, sourceEventId, actorUserId));
        return result.invoiceLineId();
    }

    @Override
    public void authorizeConsumption(UUID invoiceLineId, UUID actorUserId) {
        billingGate.authorizeConsumption(invoiceLineId, actorUserId);
    }

    @Override
    public UUID postFixedAmountCharge(UUID encounterId, UUID patientId, UUID facilityId, PayerType payerType,
                                       String serviceItemCode, Money amount, String sourceEventType, UUID sourceEventId,
                                       UUID actorUserId) {
        PayerCategory payerCategory = toPayerCategory(payerType);
        ChargePostingResult result = chargePostingService.postFixedAmountCharge(new FixedAmountChargePostingCommand(
                encounterId, patientId, facilityId, payerCategory, serviceItemCode, amount,
                sourceEventType, sourceEventId, actorUserId));
        return result.invoiceLineId();
    }

    // Pricing keyed by the provider's actual role is a catalogue lookup,
    // not an authorization decision — root CLAUDE.md "Authorisation"
    // ("check permissions, never role names") governs gating an action;
    // this resolves which price applies, the same way a role code is data
    // in PriceListEntry. V19__provider_billing_category.sql: the mapping
    // itself is data (ProviderBillingCategory), not a Java constant — the
    // highest-priority current row among the roles actually held wins, so
    // a provider holding both DOCTOR and SPECIALIST_DOCTOR still resolves
    // deterministically without this code judging which role is "the
    // acceptable kind."
    private String resolveConsultationServiceItemCode(UUID assignedProviderId) {
        Set<String> roleCodes = authorizationService.activeAssignments(new Actor(assignedProviderId)).stream()
                .map(RoleAssignmentView::roleCode)
                .collect(Collectors.toSet());
        ProviderBillingCategory category = providerBillingCategoryRepository.findHighestPriorityCurrent(roleCodes)
                .orElseThrow(() -> new IllegalStateException("Assigned provider " + assignedProviderId
                        + " holds no role mapped to a consultation price (roles held: " + roleCodes + ")."));
        return category.serviceItemCode();
    }

    private PayerCategory toPayerCategory(PayerType payerType) {
        // No insurance module: NHIF is named explicitly in V15's seed
        // comment as the dominant scheme, a reasonable placeholder — it
        // resolves to the same PriceNotConfiguredException as any other
        // unseeded category today regardless, since only CASH has prices.
        return payerType == PayerType.CASH ? PayerCategory.CASH : PayerCategory.NHIF;
    }
}
