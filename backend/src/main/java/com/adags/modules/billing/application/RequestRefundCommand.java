package com.adags.modules.billing.application;

import java.util.UUID;

public record RequestRefundCommand(UUID paymentAllocationId, long amountMinor, String reason, UUID requestedBy) {
}
