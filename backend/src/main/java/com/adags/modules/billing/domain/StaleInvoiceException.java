package com.adags.modules.billing.domain;

import java.util.UUID;

// Same pattern as encounters.domain.StaleEncounterException — the version
// predicate lives in the UPDATE itself (JdbcInvoiceRepository.issue's
// "WHERE state = 'DRAFT' AND version = :expectedVersion"), so zero rows
// updated means either a concurrent change or an attempt to issue an
// invoice that was already issued. Either way: reload and resubmit, never
// a silent overwrite or a silent duplicate issue.
public class StaleInvoiceException extends RuntimeException {

    private final UUID invoiceId;
    private final int expectedVersion;

    public StaleInvoiceException(UUID invoiceId, int expectedVersion) {
        super("Invoice " + invoiceId + " is not a DRAFT at version " + expectedVersion
                + " — it has changed or was already issued. Reload and resubmit.");
        this.invoiceId = invoiceId;
        this.expectedVersion = expectedVersion;
    }

    public UUID invoiceId() {
        return invoiceId;
    }

    public int expectedVersion() {
        return expectedVersion;
    }
}
