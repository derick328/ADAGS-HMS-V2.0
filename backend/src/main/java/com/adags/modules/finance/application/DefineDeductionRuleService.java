package com.adags.modules.finance.application;

import com.adags.modules.finance.domain.DeductionRule;
import com.adags.modules.finance.domain.DeductionRuleRepository;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// 18-admin.md §9: rules as data — "a finance-act change is a
// configuration update, not a deploy." Versioned exactly like
// SchemeCoverageRule: closes the current row for this (employmentType,
// deductionCode) and opens a new one, never an edit of rateOrAmount in place.
@Service
public class DefineDeductionRuleService {

    private final DeductionRuleRepository deductionRuleRepository;

    public DefineDeductionRuleService(DeductionRuleRepository deductionRuleRepository) {
        this.deductionRuleRepository = deductionRuleRepository;
    }

    @Transactional
    public DeductionRule define(DefineDeductionRuleCommand command) {
        deductionRuleRepository.findCurrent(command.employmentType(), command.deductionCode())
                .ifPresent(current -> deductionRuleRepository.close(current.id(), LocalDate.now()));

        Money threshold = command.thresholdMinor() == null ? null : new Money(command.thresholdMinor(), "TZS");
        DeductionRule rule = DeductionRule.define(command.employmentType(), command.deductionCode(),
                command.calculationMethod(), command.rateOrAmount(), threshold, command.isEmployerPaid(),
                command.createdBy());
        return deductionRuleRepository.insert(rule);
    }
}
