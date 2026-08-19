package com.adags.modules.insurance.domain;

import java.util.Optional;
import java.util.UUID;

public interface InsuranceClaimRepository {

    /** Always inserts a DRAFT — no claim number, mirrors Invoice.insertDraft. */
    InsuranceClaim insertDraft(InsuranceClaim claim);

    Optional<InsuranceClaim> findById(UUID id);
}
