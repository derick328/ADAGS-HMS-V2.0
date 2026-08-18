package com.adags.modules.inventory.domain;

import java.time.Instant;
import java.util.UUID;

// 12-pharmacist.md §7.2: "Managed list, not free text" — same shape as
// insurance_scheme in V6__patients_module.sql, a minimal referenceable
// list with no propose/approve workflow.
public record Supplier(UUID id, String code, String name, boolean active, Instant createdAt) {

    public static Supplier create(String code, String name) {
        return new Supplier(null, code, name, true, null);
    }
}
