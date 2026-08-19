package com.adags.modules.finance.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeductionRuleRepository {

    /** Always inserts a new, currently-open row (effectiveTo NULL) — versioning closes the old one separately. */
    DeductionRule insert(DeductionRule rule);

    /** "A change closes the current entry and opens a new one" — never an edit of rateOrAmount in place. */
    void close(UUID ruleId, LocalDate effectiveTo);

    Optional<DeductionRule> findById(UUID id);

    /** The current (effectiveTo IS NULL) row for this (employmentType, deductionCode), if any. */
    Optional<DeductionRule> findCurrent(EmploymentType employmentType, String deductionCode);

    /** Every rule effective on asOfDate for employmentType — the full set SalaryRecord computation applies. */
    List<DeductionRule> findEffective(EmploymentType employmentType, LocalDate asOfDate);
}
