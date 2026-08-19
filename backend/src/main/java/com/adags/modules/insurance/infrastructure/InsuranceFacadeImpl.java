package com.adags.modules.insurance.infrastructure;

import com.adags.modules.insurance.InsuranceFacade;
import com.adags.modules.insurance.domain.CoverageRuleNotConfiguredException;
import com.adags.modules.insurance.domain.SchemeCoverageRule;
import com.adags.modules.insurance.domain.SchemeCoverageRuleRepository;
import com.adags.modules.insurance.domain.ServiceCategory;
import com.adags.shared.kernel.CoverageSplit;
import com.adags.shared.kernel.Money;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class InsuranceFacadeImpl implements InsuranceFacade {

    private final SchemeCoverageRuleRepository schemeCoverageRuleRepository;

    public InsuranceFacadeImpl(SchemeCoverageRuleRepository schemeCoverageRuleRepository) {
        this.schemeCoverageRuleRepository = schemeCoverageRuleRepository;
    }

    @Override
    public CoverageSplit resolveCoverageSplit(UUID schemeId, UUID serviceItemId, String serviceCategory, Money gross,
                                               LocalDate serviceDate) {
        SchemeCoverageRule rule = schemeCoverageRuleRepository
                .findApplicable(schemeId, serviceItemId, ServiceCategory.valueOf(serviceCategory), serviceDate)
                .orElseThrow(() -> new CoverageRuleNotConfiguredException(
                        "No coverage rule for scheme " + schemeId + ", item " + serviceItemId
                                + " (" + serviceCategory + "), on " + serviceDate + "."));
        SchemeCoverageRule.Split split = rule.computeSplit(gross);
        return new CoverageSplit(split.insurancePortion(), split.patientPortion(), rule.id());
    }
}
