package com.adags.modules.insurance.domain;

import java.time.Instant;
import java.util.UUID;

// V6: "a minimal stub — just enough for InsurancePolicy to reference a
// managed list instead of free text." Real here (Phase 7 item 3 part 1):
// claimSubmissionMethod and contact are free text — no fixed vocabulary
// of submission channels exists across schemes, and guessing one would
// be the same mistake root CLAUDE.md "Billing" warns against for coverage
// percentages. paymentTermsDays is nullable for the same reason: unknown
// until someone at the scheme actually states it.
public record InsuranceScheme(
        UUID id,
        String code,
        String name,
        boolean active,
        String claimSubmissionMethod,
        String contact,
        Integer paymentTermsDays,
        Instant createdAt
) {

    public static InsuranceScheme create(String code, String name, String claimSubmissionMethod, String contact,
                                          Integer paymentTermsDays) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required.");
        }
        if (paymentTermsDays != null && paymentTermsDays <= 0) {
            throw new IllegalArgumentException("paymentTermsDays must be > 0 if given.");
        }
        return new InsuranceScheme(null, code, name, true, claimSubmissionMethod, contact, paymentTermsDays, null);
    }
}
