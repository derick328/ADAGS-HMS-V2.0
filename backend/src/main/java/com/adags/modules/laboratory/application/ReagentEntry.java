package com.adags.modules.laboratory.application;

import java.util.UUID;

// 15-lab-technician.md §5.5. stockBatchId is nullable — the inventory
// module doesn't exist yet (see LabResultReagent).
public record ReagentEntry(UUID stockBatchId, Double quantityUsed, String note) {
}
