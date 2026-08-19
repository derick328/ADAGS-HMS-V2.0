package com.adags.modules.billing.domain;

// root CLAUDE.md "Authorisation": the second-person constraint for a
// credit note above the configurable threshold. The DB CHECK
// (credit_note: NOT requires_second_signature OR approved_by !=
// issued_by) is the real guard; this is the friendly application-layer
// rejection before it ever reaches that constraint.
public class CreditNoteSelfApprovalNotAllowedException extends RuntimeException {

    public CreditNoteSelfApprovalNotAllowedException() {
        super("This credit note requires a second signature — the issuer cannot also approve it.");
    }
}
