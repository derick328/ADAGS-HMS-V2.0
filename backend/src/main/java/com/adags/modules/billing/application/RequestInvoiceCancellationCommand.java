package com.adags.modules.billing.application;

import java.util.UUID;

// supersedesRequestId is non-null only for reconsideration of a REJECTED
// request (18-admin.md §6.1) — null for a first-time request.
public record RequestInvoiceCancellationCommand(UUID invoiceId, String reason, UUID requestedBy, UUID supersedesRequestId) {
}
