package com.adags.modules.billing.domain;

import java.time.Instant;
import java.util.UUID;

// 02-billing-architecture.md §3.2. referenceId is deliberately untyped
// (payment, claim, bypass or deposit-account id) — the four sources don't
// share a table, so this can't be a real FK; authorizationType says which
// table to resolve it against.
//
// Not yet created by anything (Phase 3 item 2 only builds the table and
// this record) — the gate (item 3) is what sets authorization_id on a
// line, e.g. from an active PaymentBypass, and once set it is NEVER
// cleared (root CLAUDE.md "Billing": an order placed under a bypass still
// fulfils after that bypass is revoked).
public record ServiceAuthorization(
        UUID id,
        UUID invoiceLineId,
        AuthorizationType authorizationType,
        Instant authorizedAt,
        UUID authorizedBy,
        UUID referenceId
) {
}
