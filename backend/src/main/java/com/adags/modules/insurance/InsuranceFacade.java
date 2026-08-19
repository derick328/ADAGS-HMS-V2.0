package com.adags.modules.insurance;

import com.adags.shared.kernel.CoverageSplit;
import com.adags.shared.kernel.Money;

import java.time.LocalDate;
import java.util.Optional;
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

    /**
     * Phase 7 item 3 part 3: the id of the coverage rule applying on
     * serviceDate for this scope, unless it is EXCLUDED — empty in that
     * case. The billing gate's coverage clause needs both the yes/no and,
     * when yes, an id to record as the ServiceAuthorization's reference_id
     * (root CLAUDE.md "Billing" durability — same as a bypass or payment
     * grant always names what authorized it). Same resolution as
     * resolveCoverageSplit (an item-specific rule wins over a category
     * rule for the same date).
     *
     * @throws com.adags.modules.insurance.domain.CoverageRuleNotConfiguredException
     *         if no rule covers this scope/date.
     */
    Optional<UUID> resolveCoveringRuleId(UUID schemeId, UUID serviceItemId, String serviceCategory, LocalDate serviceDate);

    /**
     * Phase 7 item 3 part 3: raises one DRAFT InsuranceClaim — claim_no
     * already allocated from the gapless sequence — for invoiceId
     * against the scheme behind anyCoverageRuleId (one of the settled
     * invoice's own qualifying lines; every line built by
     * ChargePostingService shares one patient's one active policy at
     * posting time, so any of them names the right scheme). Published
     * through the outbox as ClaimRaised. Only call this once, for
     * insuranceAmount &gt; 0, when the invoice reaches
     * PATIENT_PORTION_SETTLED — never earlier: coverage satisfying the
     * gate is not the same as a claim existing.
     */
    UUID raiseClaim(UUID invoiceId, UUID facilityId, UUID anyCoverageRuleId, Money insuranceAmount, UUID actorUserId);
}
