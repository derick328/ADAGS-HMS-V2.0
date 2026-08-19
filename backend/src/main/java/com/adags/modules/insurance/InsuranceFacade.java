package com.adags.modules.insurance;

import com.adags.shared.kernel.CoverageSplit;
import com.adags.shared.kernel.Money;

import java.time.LocalDate;
import java.util.UUID;

// The one public surface other modules may depend on (root CLAUDE.md
// "Modules"). Every parameter type here is a UUID, a String, a
// shared.kernel type, or a primitive — never an insurance.domain type —
// same convention BillingFacade documents for itself.
public interface InsuranceFacade {

    /**
     * 02-billing-architecture.md §6.2/§6.3: resolves the SchemeCoverageRule
     * that applied on serviceDate for serviceItemId (falling back to
     * serviceCategory — the insurance.domain.ServiceCategory name, e.g.
     * "CONSULTATION") within schemeId, and computes the gross/insurance/
     * patient split from it.
     *
     * @throws com.adags.modules.insurance.domain.CoverageRuleNotConfiguredException
     *         if no rule covers this scope/date — never defaults to EXCLUDED or FULL;
     *         a missing rule is a configuration gap, not a business answer.
     */
    CoverageSplit resolveCoverageSplit(UUID schemeId, UUID serviceItemId, String serviceCategory, Money gross, LocalDate serviceDate);
}
