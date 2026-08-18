package com.adags.modules.billing.domain;

import com.adags.shared.kernel.Money;

import java.util.UUID;

// 02-billing-architecture.md §3.3: "A refusal without a next step leaves
// a patient at a counter." Carries exactly what backend/CLAUDE.md's API
// section requires a 402 body to contain — the outstanding amount and the
// specific service — so a lab technician's screen can say "Malaria RDT,
// 5,000/=, send patient to cashier" instead of "payment required."
public class BillingGateRejectedException extends RuntimeException {

    private final UUID invoiceLineId;
    private final Money outstandingAmount;
    private final String serviceName;

    public BillingGateRejectedException(UUID invoiceLineId, Money outstandingAmount, String serviceName) {
        super(serviceName + " (" + outstandingAmount.amountMinor() + " " + outstandingAmount.currencyCode()
                + ") is not authorized for invoice line " + invoiceLineId + " — send the patient to the cashier.");
        this.invoiceLineId = invoiceLineId;
        this.outstandingAmount = outstandingAmount;
        this.serviceName = serviceName;
    }

    public UUID invoiceLineId() {
        return invoiceLineId;
    }

    public Money outstandingAmount() {
        return outstandingAmount;
    }

    public String serviceName() {
        return serviceName;
    }
}
