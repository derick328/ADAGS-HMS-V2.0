package com.adags.modules.finance.application;

import java.util.UUID;

public record VoidExpenseCommand(UUID expenseId, UUID voidedBy, String voidReason) {
}
