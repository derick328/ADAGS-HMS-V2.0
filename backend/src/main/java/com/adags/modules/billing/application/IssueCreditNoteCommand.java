package com.adags.modules.billing.application;

import java.util.UUID;

public record IssueCreditNoteCommand(UUID invoiceId, long amountMinor, String reason, String reference, UUID issuedBy) {
}
