package com.adags.modules.insurance.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SchemeCoverageRuleRepository {

    /** Always inserts a new, currently-open row (effectiveTo NULL) — versioning closes the old one separately. */
    SchemeCoverageRule insert(SchemeCoverageRule rule);

    /** §6.2: "A change closes the current entry and opens a new one" — never an edit of coverage_value in place. */
    void close(UUID ruleId, LocalDate effectiveTo);

    /**
     * The rule governing serviceItemId under schemeId, as it stood on
     * serviceDate — never "today's" rule. An item-specific rule wins over
     * a category rule covering the same date; serviceCategory is only
     * consulted when no item-specific rule matches.
     */
    Optional<SchemeCoverageRule> findApplicable(UUID schemeId, UUID serviceItemId, ServiceCategory serviceCategory,
                                                 LocalDate serviceDate);

    /** Phase 7 item 3 part 3: raising a claim needs the schemeId behind an already-posted line's coverageRuleId. */
    Optional<SchemeCoverageRule> findById(UUID id);
}
