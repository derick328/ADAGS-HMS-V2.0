package com.adags.modules.billing.api;

import com.adags.modules.billing.domain.CreditNoteStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewCreditNoteRequest(@NotNull CreditNoteStatus decision, String decisionNote) {
}
