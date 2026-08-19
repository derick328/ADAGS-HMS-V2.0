package com.adags.modules.finance.domain;

// root CLAUDE.md "Authorisation": "CHECK (created_by != approved_by) on
// expenses" — the database CHECK (V48) is the real guard; this is the
// friendly application-layer rejection before it ever reaches that
// constraint.
public class ExpenseSelfApprovalNotAllowedException extends RuntimeException {

    public ExpenseSelfApprovalNotAllowedException() {
        super("An expense cannot be approved by the person who recorded it.");
    }
}
