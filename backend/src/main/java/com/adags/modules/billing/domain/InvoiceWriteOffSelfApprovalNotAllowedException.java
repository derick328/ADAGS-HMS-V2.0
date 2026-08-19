package com.adags.modules.billing.domain;

// root CLAUDE.md "Authorisation": the second-person constraint for a
// write-off above the configurable threshold. The DB CHECK
// (invoice_write_off: NOT requires_second_signature OR decided_by !=
// requested_by) is the real guard; this is the friendly application-layer
// rejection before it ever reaches that constraint.
public class InvoiceWriteOffSelfApprovalNotAllowedException extends RuntimeException {

    public InvoiceWriteOffSelfApprovalNotAllowedException() {
        super("This write-off requires a second signature — the requester cannot also decide it.");
    }
}
