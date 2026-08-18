package com.adags.modules.laboratory.api;

import java.util.UUID;

public record ReagentRequest(UUID stockBatchId, Double quantityUsed, String note) {
}
