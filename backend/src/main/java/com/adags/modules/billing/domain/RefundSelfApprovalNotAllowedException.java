package com.adags.modules.billing.domain;

// root CLAUDE.md "Authorisation": the second-person constraint for a
// refund above the configurable threshold. The DB CHECK (refund: NOT
// requires_second_signature OR decided_by != requested_by) is the real
// guard; this is the friendly application-layer rejection before it ever
// reaches that constraint.
public class RefundSelfApprovalNotAllowedException extends RuntimeException {

    public RefundSelfApprovalNotAllowedException() {
        super("This refund requires a second signature — the requester cannot also decide it.");
    }
}
