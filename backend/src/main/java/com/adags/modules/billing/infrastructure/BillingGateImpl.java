package com.adags.modules.billing.infrastructure;

import com.adags.modules.billing.domain.AuthorizationType;
import com.adags.modules.billing.domain.BillingGate;
import com.adags.modules.billing.domain.BillingGateRejectedException;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PaymentAllocation;
import com.adags.modules.billing.domain.PaymentAllocationRepository;
import com.adags.modules.billing.domain.PaymentBypass;
import com.adags.modules.billing.domain.PaymentBypassRepository;
import com.adags.modules.billing.domain.ServiceAuthorization;
import com.adags.modules.billing.domain.ServiceAuthorizationRepository;
import com.adags.modules.billing.domain.ServiceItem;
import com.adags.modules.billing.domain.ServiceItemRepository;
import com.adags.modules.insurance.InsuranceFacade;
import com.adags.modules.insurance.domain.CoverageRuleNotConfiguredException;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Component
public class BillingGateImpl implements BillingGate {

    private final InvoiceLineRepository invoiceLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentBypassRepository paymentBypassRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ServiceAuthorizationRepository serviceAuthorizationRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final PatientFacade patientFacade;
    private final InsuranceFacade insuranceFacade;
    private final Clock clock;

    public BillingGateImpl(
            InvoiceLineRepository invoiceLineRepository,
            InvoiceRepository invoiceRepository,
            PaymentBypassRepository paymentBypassRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            ServiceAuthorizationRepository serviceAuthorizationRepository,
            ServiceItemRepository serviceItemRepository,
            PatientFacade patientFacade,
            InsuranceFacade insuranceFacade,
            Clock clock
    ) {
        this.invoiceLineRepository = invoiceLineRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentBypassRepository = paymentBypassRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.serviceAuthorizationRepository = serviceAuthorizationRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.patientFacade = patientFacade;
        this.insuranceFacade = insuranceFacade;
        this.clock = clock;
    }

    @Override
    public boolean isSatisfied(UUID invoiceLineId) {
        InvoiceLine line = requireLine(invoiceLineId);
        return line.authorizationId() != null || evaluate(line) != null;
    }

    @Override
    public void authorizeConsumption(UUID invoiceLineId, UUID actorUserId) {
        InvoiceLine line = requireLine(invoiceLineId);
        if (line.authorizationId() != null) {
            return; // §3.1 clause 4 — durable, already authorized, nothing more to do.
        }

        Grant grant = evaluate(line);
        if (grant == null) {
            throw rejection(line);
        }
        if (grant.writesAuthorization()) {
            ServiceAuthorization authorization = serviceAuthorizationRepository.insert(
                    new ServiceAuthorization(null, line.id(), grant.authorizationType(), null, actorUserId, grant.referenceId()));
            invoiceLineRepository.setAuthorization(line.id(), authorization.id());
        }
    }

    // Tries each clause of 02-billing-architecture.md §3 in order, short-
    // circuiting on the first that holds. Returns null if none do.
    private Grant evaluate(InvoiceLine line) {
        Grant settled = patientPortionSettled(line);
        if (settled != null) {
            return settled;
        }
        Optional<UUID> coveringRuleId = verifiedInsuranceCovers(line);
        if (coveringRuleId.isPresent()) {
            return new Grant(true, AuthorizationType.INSURANCE, coveringRuleId.get());
        }
        UUID encounterId = requireInvoice(line.invoiceId()).encounterId();
        if (encounterId != null) {
            List<PaymentBypass> activeBypasses = paymentBypassRepository.findActiveByEncounterId(encounterId);
            if (!activeBypasses.isEmpty()) {
                return new Grant(true, AuthorizationType.EMERGENCY_BYPASS, activeBypasses.get(0).id());
            }
        }
        if (admissionDepositBacked(line)) {
            return new Grant(true, AuthorizationType.DEPOSIT, null);
        }
        return null;
    }

    // Two distinct cases, both real:
    //  - patientPortion is exactly zero AND no coverage rule produced it:
    //    trivially and permanently true from the line's own immutable
    //    data — nothing revocable to protect against later, so no
    //    ServiceAuthorization is written. A zero patientPortion that DID
    //    come from a coverage rule (FULL coverage) is deliberately NOT
    //    granted here — falls through to verifiedInsuranceCovers instead,
    //    so a FULL-coverage line still requires the policy to actually be
    //    VERIFIED before it's fulfillable for free.
    //  - settled (non-pending-clearance) PaymentAllocations sum to at
    //    least patientPortion: a real payment covered it. This DOES get
    //    durable protection — a later refund must not retroactively
    //    unconsume something already dispensed, same reasoning as a
    //    bypass revocation. Under the exact-match rule (addendum §1) a
    //    line normally reaches this in exactly one allocation; the last
    //    contributing settled allocation's payment is the reference.
    private Grant patientPortionSettled(InvoiceLine line) {
        if (line.patientPortion().amountMinor() == 0 && line.coverageRuleId() == null) {
            return new Grant(false, null, null);
        }
        List<PaymentAllocation> settledAllocations = paymentAllocationRepository.findSettledByInvoiceLineId(line.id());
        long settledTotal = settledAllocations.stream().mapToLong(a -> a.amount().amountMinor()).sum();
        if (settledTotal >= line.patientPortion().amountMinor() && !settledAllocations.isEmpty()) {
            UUID fundingPaymentId = settledAllocations.get(settledAllocations.size() - 1).paymentId();
            return new Grant(true, AuthorizationType.PAYMENT, fundingPaymentId);
        }
        return null;
    }

    // 02-billing-architecture.md §6.1: "gateSatisfied via insurance =
    // policy.verification_status == VERIFIED AND scheme_covers(service)
    // AND patient_portion_outstanding == 0." All four conditions are
    // checked here, independently of whatever got this line to
    // patientPortionSettled returning null (a FULL-coverage zero patient
    // portion, or a non-zero portion not yet paid) — this method is the
    // single source of truth for "does insurance make this line
    // fulfillable," not just a fallback.
    //
    // Re-resolves the coverage rule fresh via InsuranceFacade rather than
    // trusting line.coverageRuleId() blindly — "use part 1's resolver,
    // same date" (the line's own creation date, when ChargePostingService
    // resolved this exact split). A missing rule at this point is treated
    // as "not covered," not a crash: the throw-on-missing-configuration
    // discipline belongs to charge-posting time (part 2), where it
    // prevents a mispriced charge from ever being created — by gate-check
    // time the charge already exists, and demanding perfect resolvability
    // a second time would make an already-posted line permanently
    // unfulfillable instead of falling through to the ordinary rejection.
    private Optional<UUID> verifiedInsuranceCovers(InvoiceLine line) {
        Invoice invoice = requireInvoice(line.invoiceId());
        LocalDate serviceDate = line.createdAt().atZone(clock.getZone()).toLocalDate();

        if (!patientFacade.hasVerifiedActiveInsurancePolicy(invoice.patientId(), serviceDate)) {
            return Optional.empty();
        }
        Optional<UUID> schemeId = patientFacade.resolveActiveInsuranceSchemeId(invoice.patientId(), serviceDate);
        if (schemeId.isEmpty()) {
            return Optional.empty();
        }
        ServiceItem serviceItem = serviceItemRepository.findById(line.serviceItemId())
                .orElseThrow(() -> new NoSuchElementException("No service item " + line.serviceItemId()));
        Optional<UUID> coveringRuleId;
        try {
            coveringRuleId = insuranceFacade.resolveCoveringRuleId(
                    schemeId.get(), serviceItem.id(), serviceItem.category().name(), serviceDate);
        } catch (CoverageRuleNotConfiguredException noRuleFound) {
            return Optional.empty();
        }
        if (coveringRuleId.isEmpty()) {
            return Optional.empty();
        }

        long settledMinor = paymentAllocationRepository.findSettledByInvoiceLineId(line.id()).stream()
                .mapToLong(a -> a.amount().amountMinor())
                .sum();
        return settledMinor >= line.patientPortion().amountMinor() ? coveringRuleId : Optional.empty();
    }

    // No inpatient module (DepositAccount, DepositTransaction) — same
    // honest-stub treatment as verifiedInsuranceCovers.
    private boolean admissionDepositBacked(InvoiceLine line) {
        return false;
    }

    private BillingGateRejectedException rejection(InvoiceLine line) {
        ServiceItem serviceItem = serviceItemRepository.findById(line.serviceItemId())
                .orElseThrow(() -> new NoSuchElementException("No service item " + line.serviceItemId()));
        // Net off any settled partial (bypass-only — addendum §1) already
        // recorded against this line, so the destination amount is what
        // is actually still owed, not the original full patientPortion.
        long settledMinor = paymentAllocationRepository.findSettledByInvoiceLineId(line.id()).stream()
                .mapToLong(a -> a.amount().amountMinor())
                .sum();
        Money outstanding = line.patientPortion().minus(new Money(settledMinor, line.patientPortion().currencyCode()));
        return new BillingGateRejectedException(line.id(), outstanding, serviceItem.name());
    }

    private InvoiceLine requireLine(UUID invoiceLineId) {
        return invoiceLineRepository.findById(invoiceLineId)
                .orElseThrow(() -> new NoSuchElementException("No invoice line " + invoiceLineId));
    }

    private Invoice requireInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NoSuchElementException("No invoice " + invoiceId));
    }

    private record Grant(boolean writesAuthorization, AuthorizationType authorizationType, UUID referenceId) {
    }
}
