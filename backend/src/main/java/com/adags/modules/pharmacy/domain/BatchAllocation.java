package com.adags.modules.pharmacy.domain;

import java.util.UUID;

public record BatchAllocation(UUID batchId, int quantity) {
}
