package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.Invoice;
import com.adags.modules.billing.domain.InvoiceRepository;
import com.adags.modules.billing.domain.PaymentBypass;
import com.adags.modules.billing.domain.PaymentBypassRepository;
import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.patients.PatientFacade;
import com.adags.shared.kernel.EncounterContext;
import com.adags.shared.kernel.PatientIdentity;
import org.springframework.stereotype.Service;

import java.util.List;

// 02-billing-architecture.md §5.4: the emergency billing bar — every
// encounter with an active PaymentBypass, its live accrued balance, hidden
// entirely when empty. Balance is live (invoiceRepository.search, not a
// stored total): §5.4 is explicit this is "not a fixed consultation fee."
@Service
public class ActivePaymentBypassOverviewService {

    private final PaymentBypassRepository paymentBypassRepository;
    private final InvoiceRepository invoiceRepository;
    private final EncounterFacade encounterFacade;
    private final PatientFacade patientFacade;

    public ActivePaymentBypassOverviewService(
            PaymentBypassRepository paymentBypassRepository,
            InvoiceRepository invoiceRepository,
            EncounterFacade encounterFacade,
            PatientFacade patientFacade
    ) {
        this.paymentBypassRepository = paymentBypassRepository;
        this.invoiceRepository = invoiceRepository;
        this.encounterFacade = encounterFacade;
        this.patientFacade = patientFacade;
    }

    public List<EmergencyBypassRow> overview() {
        return paymentBypassRepository.findAllActive().stream()
                .map(this::toRow)
                .filter(row -> row != null)
                .toList();
    }

    private EmergencyBypassRow toRow(PaymentBypass bypass) {
        EncounterContext context = encounterFacade.findContext(bypass.encounterId()).orElse(null);
        if (context == null) {
            return null;
        }
        PatientIdentity identity = patientFacade.findIdentity(context.patientId()).orElse(null);

        long outstandingMinor = 0;
        String currencyCode = "TZS";
        List<Invoice> outstandingInvoices = invoiceRepository.search(
                null, bypass.encounterId(), null, null, null, true);
        for (Invoice invoice : outstandingInvoices) {
            outstandingMinor += invoice.outstandingTotal().amountMinor();
            currencyCode = invoice.outstandingTotal().currencyCode();
        }

        return new EmergencyBypassRow(bypass.id(), bypass.encounterId(), context.patientId(),
                identity == null ? null : identity.fullName(), identity == null ? null : identity.hospitalNumber(),
                bypass.grantedAt(), bypass.reason(), outstandingMinor, currencyCode);
    }
}
