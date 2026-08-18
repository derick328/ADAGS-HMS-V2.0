package com.adags.modules.billing.domain;

// 02-billing-architecture.md §9.1: "the cashier declares, they do not
// reconcile their own drawer." Same shape as
// referencedata.domain.SelfReviewNotAllowedException — the friendly,
// pre-flight version of the real guard, a DB CHECK on cashier_shift
// (reconciled_by != closed_by).
public class SelfReconciliationNotAllowedException extends RuntimeException {

    public SelfReconciliationNotAllowedException() {
        super("A cashier shift cannot be reconciled by the same user who closed it.");
    }
}
