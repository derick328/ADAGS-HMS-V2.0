package com.adags.modules.finance.application;

import java.util.UUID;

public record ApproveExpenseCommand(UUID expenseId, UUID approvedBy, String approvalNote) {
}
