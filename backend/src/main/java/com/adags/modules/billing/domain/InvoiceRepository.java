package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {

    /** Always inserts a DRAFT — no invoice number, freely editable. */
    Invoice insertDraft(Invoice invoice);

    Optional<Invoice> findById(UUID id);

    /**
     * DRAFT -> ISSUED: assigns invoiceNumber (from the gapless sequence,
     * allocated by the caller) and recomputes gross/insurance/patient
     * totals from the invoice's current lines, in the same statement that
     * locks the invoice at expectedVersion. 02-billing-architecture.md
     * §10: the number is allocated at issue, never at draft.
     *
     * @throws StaleInvoiceException if the invoice is no longer DRAFT at expectedVersion.
     */
    Invoice issue(UUID invoiceId, String invoiceNumber, int expectedVersion);

    /**
     * 02-billing-architecture.md §9.3: decrements outstandingTotal by
     * settledAmount and, if it reaches zero, transitions ISSUED -&gt;
     * PATIENT_PORTION_SETTLED in the same statement. Idempotent against
     * being called after the transition already happened — the state
     * only ever moves forward.
     */
    Invoice recordSettlement(UUID invoiceId, Money settledAmount);

    /**
     * ISSUED -&gt; VOID, only when nothing has been paid against it
     * (outstandingTotal == grossTotal). §10: "Once any payment has
     * landed, void is blocked" — a partially or fully paid invoice needs
     * the accountant's cancellation-request flow instead (not built yet).
     *
     * @throws InvoiceCannotBeVoidedException if the invoice is not ISSUED or has a settled amount.
     */
    Invoice voidUnpaid(UUID invoiceId, String reason, UUID voidedBy);
}
