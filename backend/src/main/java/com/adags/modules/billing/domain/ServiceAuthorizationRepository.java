package com.adags.modules.billing.domain;

// No caller yet — the gate (Phase 3 item 3) is the first one to insert a
// row here, e.g. when an active PaymentBypass satisfies a line. Built now
// because InvoiceLine.authorizationId's FK target must exist.
public interface ServiceAuthorizationRepository {

    ServiceAuthorization insert(ServiceAuthorization authorization);
}
