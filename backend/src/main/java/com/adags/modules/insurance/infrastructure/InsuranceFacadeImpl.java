package com.adags.modules.insurance.infrastructure;

import com.adags.modules.insurance.InsuranceFacade;
import com.adags.modules.insurance.domain.CoverageRuleNotConfiguredException;
import com.adags.modules.insurance.domain.CoverageType;
import com.adags.modules.insurance.domain.InsuranceClaim;
import com.adags.modules.insurance.domain.InsuranceClaimRepository;
import com.adags.modules.insurance.domain.SchemeCoverageRule;
import com.adags.modules.insurance.domain.SchemeCoverageRuleRepository;
import com.adags.modules.insurance.domain.ServiceCategory;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.kernel.CoverageSplit;
import com.adags.shared.kernel.Money;
import com.adags.shared.numbering.GaplessNumberAllocator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Component
public class InsuranceFacadeImpl implements InsuranceFacade {

    private static final String CLAIM_NUMBER_SEQUENCE = "CLAIM_NO";
    private static final int CLAIM_NUMBER_PAD_WIDTH = 6;

    private final SchemeCoverageRuleRepository schemeCoverageRuleRepository;
    private final InsuranceClaimRepository insuranceClaimRepository;
    private final GaplessNumberAllocator numberAllocator;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public InsuranceFacadeImpl(
            SchemeCoverageRuleRepository schemeCoverageRuleRepository,
            InsuranceClaimRepository insuranceClaimRepository,
            GaplessNumberAllocator numberAllocator,
            DomainEventPublisher eventPublisher,
            Clock clock
    ) {
        this.schemeCoverageRuleRepository = schemeCoverageRuleRepository;
        this.insuranceClaimRepository = insuranceClaimRepository;
        this.numberAllocator = numberAllocator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public CoverageSplit resolveCoverageSplit(UUID schemeId, UUID serviceItemId, String serviceCategory, Money gross,
                                               LocalDate serviceDate) {
        SchemeCoverageRule rule = requireApplicableRule(schemeId, serviceItemId, serviceCategory, serviceDate);
        SchemeCoverageRule.Split split = rule.computeSplit(gross);
        return new CoverageSplit(split.insurancePortion(), split.patientPortion(), rule.id());
    }

    @Override
    public Optional<UUID> resolveCoveringRuleId(UUID schemeId, UUID serviceItemId, String serviceCategory, LocalDate serviceDate) {
        SchemeCoverageRule rule = requireApplicableRule(schemeId, serviceItemId, serviceCategory, serviceDate);
        return rule.coverageType() == CoverageType.EXCLUDED ? Optional.empty() : Optional.of(rule.id());
    }

    @Override
    public UUID raiseClaim(UUID invoiceId, UUID facilityId, UUID anyCoverageRuleId, Money insuranceAmount, UUID actorUserId) {
        SchemeCoverageRule rule = schemeCoverageRuleRepository.findById(anyCoverageRuleId)
                .orElseThrow(() -> new NoSuchElementException("Coverage rule not found: " + anyCoverageRuleId));
        String claimNo = numberAllocator.allocate(
                CLAIM_NUMBER_SEQUENCE, facilityId, LocalDate.now(clock).getYear(), CLAIM_NUMBER_PAD_WIDTH);
        InsuranceClaim claim = insuranceClaimRepository.insert(
                InsuranceClaim.raise(invoiceId, rule.schemeId(), claimNo));

        eventPublisher.publish("ClaimRaised", Map.of(
                "claimId", claim.id().toString(),
                "invoiceId", invoiceId.toString(),
                "schemeId", rule.schemeId().toString(),
                "claimNo", claim.claimNo(),
                "insuranceAmountMinor", insuranceAmount.amountMinor()));
        return claim.id();
    }

    private SchemeCoverageRule requireApplicableRule(UUID schemeId, UUID serviceItemId, String serviceCategory,
                                                       LocalDate serviceDate) {
        return schemeCoverageRuleRepository
                .findApplicable(schemeId, serviceItemId, ServiceCategory.valueOf(serviceCategory), serviceDate)
                .orElseThrow(() -> new CoverageRuleNotConfiguredException(
                        "No coverage rule for scheme " + schemeId + ", item " + serviceItemId
                                + " (" + serviceCategory + "), on " + serviceDate + "."));
    }
}
