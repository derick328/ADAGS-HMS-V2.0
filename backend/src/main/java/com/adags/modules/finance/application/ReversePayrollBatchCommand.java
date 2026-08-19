package com.adags.modules.finance.application;

import java.util.UUID;

public record ReversePayrollBatchCommand(UUID batchId, UUID reversedBy, String reason) {
}
