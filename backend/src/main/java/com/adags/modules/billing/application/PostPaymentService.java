package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.BillingGate;
import com.adags.modules.billing.domain.CashierShift;
import com.adags.modules.billing.domain.CashierShiftNotOpenException;
import com.adags.modules.billing.domain.CashierShiftRepository;
import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceLine;
import com.adags.modules.billing.domain.InvoiceLineRepository;
import com.adags.modules.billing.domain.InvoiceNotPayableException;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.InvoiceState;
import com.adags.modules.billing.domain.Payment;
import com.adags.modules.billing.domain.PaymentAllocation;
import com.adags.modules.billing.domain.PaymentAllocationRejectedException;
import com.adags.modules.billing.domain.PaymentAllocationRepository;
import com.adags.modules.billing.domain.PaymentBypassRepository;
import com.adags.modules.billing.domain.PaymentMethod;
import com.adags.modules.billing.domain.PaymentRepository;
import com.adags.modules.billing.domain.PaymentStatus;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.Money;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// 02-billing-architecture.md §9, 03-billing-addendum.md §1. The
// receptionist's line picker: every allocation is validated before
// anything is written, so a rejected line fails the whole payment rather
// than leaving a partially-recorded one.
@Service
public class PostPaymentService {

    private static final String CURRENCY_CODE = "TZS";

    private final CashierShiftRepository cashierShiftRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final PaymentBypassRepository paymentBypassRepository;
    private final BillingGate billingGate;
    private final DomainEventPublisher eventPublisher;

    public PostPaymentService(
            CashierShiftRepository cashierShiftRepository,
            PaymentRepository paymentRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            PaymentBypassRepository paymentBypassRepository,
            BillingGate billingGate,
            DomainEventPublisher eventPublisher
    ) {
        this.cashierShiftRepository = cashierShiftRepository;
        this.paymentRepository = paymentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.paymentBypassRepository = paymentBypassRepository;
        this.billingGate = billingGate;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Payment recordPayment(PostPaymentCommand command) {
        // §9.2: "A repeat returns the original payment, it does not
        // create a second or throw." Checked first so a retried request
        // never re-validates or re-processes anything.
        var existing = paymentRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        CashierShift shift = cashierShiftRepository.findOpenByOpenedBy(command.actorUserId())
                .orElseThrow(() -> new CashierShiftNotOpenException(
                        "No open cashier shift for user " + command.actorUserId() + " — open a shift before recording a payment."));

        List<ValidatedAllocation> validated = validateAllocations(command);
        long totalMinor = validated.stream().mapToLong(ValidatedAllocation::amountMinor).sum();

        PaymentStatus status = command.method() == PaymentMethod.CHEQUE
                ? PaymentStatus.PENDING_CLEARANCE : PaymentStatus.SETTLED;
        Payment toInsert = Payment.receive(command.patientId(), shift.id(), new Money(totalMinor, CURRENCY_CODE),
                command.method(), command.reference(), status, command.idempotencyKey(), command.actorUserId());

        Payment saved;
        try {
            saved = paymentRepository.insert(toInsert);
        } catch (DuplicateKeyException raced) {
            // Idempotency key UNIQUE index lost the race to a concurrent
            // identical request — return that one, not a second payment.
            return paymentRepository.findByIdempotencyKey(command.idempotencyKey()).orElseThrow();
        }

        for (ValidatedAllocation v : validated) {
            paymentAllocationRepository.insert(PaymentAllocation.allocate(
                    saved.id(), v.line().id(), new Money(v.amountMinor(), CURRENCY_CODE)));

            if (saved.status() != PaymentStatus.SETTLED) {
                continue; // PENDING_CLEARANCE cheque — no gate or outstanding effect until it clears.
            }

            Invoice before = invoiceRepository.findById(v.invoice().id()).orElseThrow();
            Invoice after = invoiceRepository.recordSettlement(v.invoice().id(), new Money(v.amountMinor(), CURRENCY_CODE));
            if (before.state() == InvoiceState.ISSUED && after.state() == InvoiceState.PATIENT_PORTION_SETTLED) {
                // §11.1: must survive a crash — outbox, not a direct call.
                eventPublisher.publish("InvoiceSettled", Map.of(
                        "invoiceId", after.id().toString(),
                        "patientId", after.patientId().toString(),
                        "encounterId", after.encounterId() == null ? "" : after.encounterId().toString()));
            }

            long totalSettledForLine = v.alreadySettledBefore() + v.amountMinor();
            boolean lineFullyCovered = v.line().patientPortion().amountMinor() > 0
                    && totalSettledForLine >= v.line().patientPortion().amountMinor();
            if (lineFullyCovered) {
                // The line's own ServiceAuthorization, type PAYMENT —
                // durable, survives a later refund the same way a bypass
                // authorization survives revocation.
                billingGate.authorizeConsumption(v.line().id(), command.actorUserId());
            }
        }

        return saved;
    }

    private List<ValidatedAllocation> validateAllocations(PostPaymentCommand command) {
        List<ValidatedAllocation> validated = new ArrayList<>();
        for (PostPaymentCommand.LineAllocationRequest request : command.allocations()) {
            if (request.amountMinor() <= 0) {
                throw new PaymentAllocationRejectedException(
                        "Invoice line " + request.invoiceLineId() + ": allocation amount must be > 0.");
            }

            InvoiceLine line = invoiceLineRepository.findById(request.invoiceLineId())
                    .orElseThrow(() -> new NoSuchElementException("No invoice line " + request.invoiceLineId()));
            Invoice invoice = invoiceRepository.findById(line.invoiceId())
                    .orElseThrow(() -> new NoSuchElementException("No invoice " + line.invoiceId()));
            if (invoice.state() == InvoiceState.VOID || invoice.state() == InvoiceState.CANCELLATION_PENDING) {
                throw new InvoiceNotPayableException(invoice.id(), invoice.state());
            }

            long patientPortion = line.patientPortion().amountMinor();
            long alreadySettled = settledSum(line.id());
            if (patientPortion > 0 && alreadySettled >= patientPortion) {
                throw new PaymentAllocationRejectedException(
                        "Invoice line " + line.id() + " is already fully paid.");
            }
            long remaining = patientPortion - alreadySettled;

            boolean activeBypass = invoice.encounterId() != null
                    && !paymentBypassRepository.findActiveByEncounterId(invoice.encounterId()).isEmpty();

            if (request.amountMinor() > remaining) {
                throw new PaymentAllocationRejectedException(
                        "Invoice line " + line.id() + ": cannot allocate " + request.amountMinor()
                                + " against " + remaining + " outstanding.");
            }
            // 03-billing-addendum.md §1: exact match required, unless an
            // active bypass permits a partial that accrues.
            if (request.amountMinor() != remaining && !activeBypass) {
                throw new PaymentAllocationRejectedException(
                        "Invoice line " + line.id() + " requires exact payment of " + remaining
                                + " — partial payment is only accepted under an active PaymentBypass.");
            }

            validated.add(new ValidatedAllocation(line, invoice, request.amountMinor(), alreadySettled));
        }
        return validated;
    }

    private long settledSum(UUID invoiceLineId) {
        return paymentAllocationRepository.findSettledByInvoiceLineId(invoiceLineId).stream()
                .mapToLong(a -> a.amount().amountMinor())
                .sum();
    }

    private record ValidatedAllocation(InvoiceLine line, Invoice invoice, long amountMinor, long alreadySettledBefore) {
    }
}
