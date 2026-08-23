package com.adags.modules.billing.domain;

import java.util.List;
import java.util.UUID;

public interface PaymentBypassRepository {

    PaymentBypass insert(PaymentBypass paymentBypass);

    /** 02-billing-architecture.md §3: "active_payment_bypass_exists(encounter)" — the gate-function clause. */
    List<PaymentBypass> findActiveByEncounterId(UUID encounterId);

    /** 02-billing-architecture.md §5.4: the emergency billing bar's membership — every ACTIVE bypass, facility-wide. */
    List<PaymentBypass> findAllActive();
}
