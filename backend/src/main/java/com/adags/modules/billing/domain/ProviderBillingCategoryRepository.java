package com.adags.modules.billing.domain;

import java.util.Optional;
import java.util.Set;

public interface ProviderBillingCategoryRepository {

    /**
     * Among the current (validTo IS NULL) rows whose role_code is in
     * roleCodes, the one with the highest priority — data-ordered, so
     * resolving which price applies to a provider holding multiple mapped
     * roles is never a code-level judgement call.
     */
    Optional<ProviderBillingCategory> findHighestPriorityCurrent(Set<String> roleCodes);
}
