package com.adags.modules.billing.application;

import java.util.UUID;

public record RequestInvoiceWriteOffCommand(UUID invoiceId, long amountMinor, String reason, UUID requestedBy) {
}
