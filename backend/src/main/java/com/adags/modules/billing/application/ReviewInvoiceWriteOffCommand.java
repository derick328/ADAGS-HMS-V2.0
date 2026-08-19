package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.WriteOffStatus;

import java.util.UUID;

public record ReviewInvoiceWriteOffCommand(UUID writeOffId, WriteOffStatus decision, UUID decidedBy, String decisionNote) {
}
