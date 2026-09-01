package com.adags.modules.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 12-pharmacist.md §7.2: "Managed list, not free text" — but the pharmacist
// still needs a way to add a supplier the seed list doesn't cover. This is
// the smallest possible surface for that: the pharmacist types a name, the
// system generates a code (upper-cased first token, disambiguated with a
// short suffix on collision), everything else defaults.
public record CreateSupplierRequest(
        @NotBlank(message = "Enter a supplier name")
        @Size(max = 120)
        String name
) {
}
