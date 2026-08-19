package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.CreditNoteStatus;

import java.util.UUID;

public record ReviewCreditNoteCommand(UUID creditNoteId, CreditNoteStatus decision, UUID decidedBy, String decisionNote) {
}
