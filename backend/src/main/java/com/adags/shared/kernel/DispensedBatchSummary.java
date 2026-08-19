package com.adags.shared.kernel;

import java.util.UUID;

// 17-ward-nurse.md §5.2: "the batch comes from the Dispense record" — what a
// nurse picks from when a prescription line's dispensing spanned more than
// one batch. quantityDispensed is that batch's share of the total, not a
// remaining-stock figure — administering never decrements it again.
public record DispensedBatchSummary(UUID batchId, String batchNumber, int quantityDispensed) {
}
