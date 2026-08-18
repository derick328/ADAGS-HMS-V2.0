package com.adags.modules.pharmacy.application;

import java.util.UUID;

public record DispenseLineInput(UUID batchId, int quantity, String overrideReason) {
}
